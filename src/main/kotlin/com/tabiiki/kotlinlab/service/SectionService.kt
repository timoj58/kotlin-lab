package com.tabiiki.kotlinlab.service

import com.tabiiki.kotlinlab.factory.SignalMessage
import com.tabiiki.kotlinlab.factory.SignalValue
import com.tabiiki.kotlinlab.model.Transport
import com.tabiiki.kotlinlab.repo.JourneyRepo
import com.tabiiki.kotlinlab.repo.LineInstructions
import com.tabiiki.kotlinlab.repo.LineRepo
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

interface SectionService {
    suspend fun add(transport: Transport, channel: Channel<Transport>)
    suspend fun init(line: String)
    fun isClear(section: Pair<String, String>, incoming: Boolean = false): Boolean
    fun isClearWithPriority(section: Pair<String, String>): Pair<Boolean, Int>
    fun initQueues(key: Pair<String, String>)
    fun diagnostics(transports: List<UUID>?)
    fun areSectionsClear(transport: Transport, lineInstructions: LineInstructions): Boolean
}

@Service
class SectionServiceImpl(
    @Value("\${network.minimum-hold}") private val minimumHold: Int,
    private val signalService: SignalService,
    private val journeyRepo: JourneyRepo,
    private val lineRepo: LineRepo
) : SectionService {

    private val queues = Queues(minimumHold, journeyRepo)
    private val diagnostics = Diagnostics()

    override suspend fun add(transport: Transport, channel: Channel<Transport>): Unit = coroutineScope {
        if (queues.getQueue(transport.section()).stream().anyMatch { it.id == transport.id })
            throw RuntimeException("${transport.id} being added twice to ${transport.section()}")

        holdChannels[transport.id] = Pair(AtomicBoolean(false), channel)
        queues.release(transport.section(), transport)
        jobs[transport.id] = launch {
            transport.track(queues.getChannel(transport.section()))
        }

        launch { transport.signal(signalService.getChannel(transport.section())!!) }
    }

    override suspend fun init(line: String): Unit = coroutineScope {
        queues.getQueueKeys().filter { it.first.contains(line) }.forEach {
            launch { signalService.init(it) }
            launch { monitor(it) }
        }
    }

    override fun isClear(section: Pair<String, String>, incoming: Boolean): Boolean =
        queues.isClear(section, incoming).first

    override fun isClearWithPriority(section: Pair<String, String>): Pair<Boolean, Int> =
        queues.isClear(section, true)

    override fun initQueues(key: Pair<String, String>) {
        queues.initQueues(key)
    }

    override fun diagnostics(transports: List<UUID>?) {
        diagnostics.dump(queues, transports)
    }

    private suspend fun monitor(key: Pair<String, String>) = coroutineScope {
        val channel = queues.getChannel(key)
        do {
            val msg = channel.receive()
            when (msg.atPlatform()) {
                true -> {
                    queues.getQueue(key).removeFirstOrNull()?.let {
                        jobs[it.id]!!.cancelAndJoin()
                        launch { arrive(it) }
                    }
                }
                else -> {}
            }
            when (msg.isStationary()) {
                false -> {
                    if (!holdChannels[msg.id]!!.first.get() && msg.getJourneyTime().second > minimumHold) {
                        holdChannels[msg.id]!!.first.set(true)
                        launch {
                            signalService.send(
                                msg.platformKey(), SignalMessage(
                                    signalValue = SignalValue.GREEN,
                                    id = msg.id,
                                    key = msg.section()
                                )
                            )
                        }
                    }
                }
                else -> {}
            }

        } while (true)
    }

    override fun areSectionsClear(transport: Transport, lineInstructions: LineInstructions): Boolean {
        var isClear = true
        val line = transport.line.name
        val platformToKey = Pair("$line:${lineInstructions.direction}", "$line:${lineInstructions.to.id}")

        outer@ for (key in lineRepo.getPreviousSections(platformToKey)) {
            if (!isClear(key, true)) {
                isClear = false
                break@outer
            }
        }
        return isClear
    }


    private suspend fun arrive(transport: Transport) = coroutineScope {
        transport.journal.add(
            Transport.Companion.JournalRecord(
                action = Transport.Companion.JournalActions.PLATFORM_HOLD,
                key = transport.platformToKey()!!,
                signal = SignalValue.RED
            )
        )
        journeyRepo.addJourneyTime(transport.getJourneyTime())
        holdChannels[transport.id]!!.second.send(transport)
    }

    companion object {
        private val log = LoggerFactory.getLogger(this.javaClass)
        private val jobs: ConcurrentHashMap<UUID, Job> = ConcurrentHashMap()
        private val holdChannels: ConcurrentHashMap<UUID, Pair<AtomicBoolean, Channel<Transport>>> = ConcurrentHashMap()

        class Queues(private val minimumHold: Int, private val journeyRepo: JourneyRepo) {
            private val queues: ConcurrentHashMap<Pair<String, String>, Pair<Channel<Transport>, ArrayDeque<Transport>>> =
                ConcurrentHashMap()

            fun getQueue(key: Pair<String, String>): ArrayDeque<Transport> =
                queues[key]!!.second

            fun initQueues(key: Pair<String, String>) {
                queues[key] = Pair(Channel(), ArrayDeque())
            }

            //TODO need to refactor this..make it cleaner
            fun isClear(section: Pair<String, String>, incoming: Boolean): Pair<Boolean, Int> =
                Pair(
                    queues[section]!!.second.isEmpty()
                            || (queues[section]!!.second.size < 2
                            && (
                            (!incoming && checkDistanceTravelled(
                                section,
                                queues[section]!!.second.last().getPosition(),
                                incoming
                            )
                                    && !queues[section]!!.second.last().isStationary())
                                    || (incoming && checkDistanceTravelled(
                                section,
                                queues[section]!!.second.first().getPosition(),
                                incoming
                            ))
                            )
                            && journeyRepo.getJourneyTime(section, minimumHold + 1).first > minimumHold),
                    queues[section]!!.second.lastOrNull()?.getJourneyTime()?.second ?: 0
                )

            private fun checkDistanceTravelled(
                section: Pair<String, String>,
                currentPosition: Double,
                incoming: Boolean
            ): Boolean {
                val journey = journeyRepo.getJourneyTime(section, 0)
                if (journey.second == 0.0 && !incoming) return true
                val predictedDistance = (journey.second / journey.first) * minimumHold
                return if (!incoming) currentPosition > predictedDistance else currentPosition < predictedDistance
            }

            fun getQueueKeys(): List<Pair<String, String>> = queues.keys().toList()

            fun release(key: Pair<String, String>, transport: Transport) {
                if (queues[key]!!.second.size >= 2) throw RuntimeException("Only two transporters allowed in $key")

                queues[key]!!.second.addLast(transport)
                transport.journal.add(
                    Transport.Companion.JournalRecord(
                        action = Transport.Companion.JournalActions.RELEASE, key = key
                    )
                )
            }

            fun getChannel(key: Pair<String, String>): Channel<Transport> = queues[key]!!.first
        }

        class Diagnostics {

            fun dump(queues: Queues, transports: List<UUID>?) {
                val items = mutableListOf<Transport.Companion.JournalRecord>()

                queues.getQueueKeys().forEach { queue ->
                    queues.getQueue(queue).forEach {
                        log.info("${it.id} current instruction ${it.getCurrentInstruction()} in ${it.section()}")
                    }
                }

                queues.getQueueKeys().forEach { queue ->
                    val toAdd = queues.getQueue(queue)
                        .filter { t -> transports == null || transports.contains(t.id) }
                        .map { m -> m.journal.getLog().sortedBy { l -> l.milliseconds }.takeLast(5) }
                        .flatten()
                    items.addAll(toAdd)
                }

                items.sortedBy { it.milliseconds }
                    .forEach { log.info(it.print()) }
            }
        }
    }
}