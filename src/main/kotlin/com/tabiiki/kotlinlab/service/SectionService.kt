package com.tabiiki.kotlinlab.service

import com.tabiiki.kotlinlab.factory.SignalMessage
import com.tabiiki.kotlinlab.factory.SignalValue
import com.tabiiki.kotlinlab.model.Transport
import com.tabiiki.kotlinlab.repo.JourneyRepo
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.util.Optional
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

interface SectionService {
    suspend fun add(transport: Transport, channel: Channel<Transport>)
    suspend fun init(line: String)
    fun isClear(section: Pair<String, String>, checkTime: Boolean = true): Boolean
    fun initQueues(key: Pair<String, String>)
    fun diagnostics(transports: List<UUID>)
}

@Service
class SectionServiceImpl(
    @Value("\${network.minimum-hold}") private val minimumHold: Int,
    private val signalService: SignalService,
    private val journeyRepo: JourneyRepo
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

    override fun isClear(section: Pair<String, String>, checkTime: Boolean): Boolean =
        queues.isClear(section, checkTime)

    override fun initQueues(key: Pair<String, String>) {
        queues.initQueues(key)

    }

    override fun diagnostics(transports: List<UUID>) {
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
                                    id = Optional.of(msg.id),
                                    key = Optional.of(msg.section())
                                )
                            )
                        }
                    }
                }
                else -> {}
            }

        } while (true)
    }

    private suspend fun arrive(transport: Transport) = coroutineScope {
        println("arriving ${transport.id} to ${transport.platformToKey()}")
        transport.journal.add(
            Transport.Companion.JournalRecord(
                action = Transport.Companion.JournalActions.PLATFORM_HOLD,
                key = transport.platformToKey().get(),
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

            fun isClear(section: Pair<String, String>, checkTime: Boolean): Boolean = queues[section]!!.second.isEmpty()
                    || (queues[section]!!.second.size < 2
                    && (!checkTime || checkTime && queues[section]!!.second.last()
                .getJourneyTime().second > minimumHold)
                    && !queues[section]!!.second.last().isStationary()
                    && journeyRepo.getJourneyTime(section) > minimumHold)

            fun getQueueKeys(): List<Pair<String, String>> = queues.keys().toList()

            fun release(key: Pair<String, String>, transport: Transport) {
                if (queues[key]!!.second.size >= 2) throw RuntimeException("Only two transporters allowed in $key")

                queues[key]!!.second.addLast(transport)
                println("releasing ${transport.id} to $key")
                transport.journal.add(
                    Transport.Companion.JournalRecord(
                        action = Transport.Companion.JournalActions.RELEASE, key = key
                    )
                )
            }

            fun getChannel(key: Pair<String, String>): Channel<Transport> = queues[key]!!.first
        }

        class Diagnostics {

            fun dump(queues: Queues, transports: List<UUID>) {
                val items = mutableListOf<Transport.Companion.JournalRecord>()

                queues.getQueueKeys().forEach { queue ->
                    queues.getQueue(queue).forEach {
                        log.info("${it.id} current instruction ${it.getCurrentInstruction()} in ${it.section()}")
                    }
                }

                queues.getQueueKeys().forEach { queue ->
                    val toAdd = queues.getQueue(queue)
                        .filter { t -> transports.contains(t.id) }
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