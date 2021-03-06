package com.tabiiki.kotlinlab.service

import com.tabiiki.kotlinlab.factory.SignalMessage
import com.tabiiki.kotlinlab.factory.SignalValue
import com.tabiiki.kotlinlab.model.Transport
import com.tabiiki.kotlinlab.monitor.SectionMonitor
import com.tabiiki.kotlinlab.repo.JourneyRepo
import com.tabiiki.kotlinlab.repo.LineInstructions
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
import java.util.function.Consumer

interface SectionService {
    suspend fun accept(transport: Transport, channel: Channel<Transport>)
    suspend fun init(line: String)
    fun isClear(section: Pair<String, String>, incoming: Boolean = false): Boolean
    fun isClear(transport: Transport, incoming: Boolean = false): Boolean
    fun isClearWithPriority(section: Pair<String, String>): Pair<Boolean, Int>
    fun isSwitchPlatform(transport: Transport, section: Pair<String, String>, destination: Boolean = false): Boolean
    fun initQueues(key: Pair<String, String>)
    fun diagnostics(transports: List<UUID>?)
    fun areSectionsClear(
        transport: Transport,
        lineInstructions: LineInstructions,
        sections: (Pair<String, String>) -> List<Pair<String, String>>
    ): Boolean
}

@Service
class SectionServiceImpl(
    @Value("\${network.minimum-hold}") private val minimumHold: Int,
    private val switchService: SwitchService,
    private val signalService: SignalService,
    private val journeyRepo: JourneyRepo,
) : SectionService {

    private val queues = Queues(minimumHold, journeyRepo)
    private val diagnostics = Diagnostics()
    private val sectionMonitor = SectionMonitor()

    override suspend fun accept(transport: Transport, channel: Channel<Transport>): Unit = coroutineScope {
        if (queues.getQueue(transport.section()).stream().anyMatch { it.id == transport.id })
            throw RuntimeException("${transport.id} being added twice to ${transport.section()}")

        holdChannels[transport.id] = channel
        prepareRelease(transport) { t -> launch { release(t) } }
    }

    override suspend fun init(line: String): Unit = coroutineScope {
        queues.getQueueKeys().filter { it.first.contains(line) }.forEach {
            launch { signalService.init(it) }
            launch {
                sectionMonitor.monitor(it, queues.getChannel(it))
                { k -> queues.getQueue(k).removeFirstOrNull()?.let { launch { arrive(it) } } }
            }
        }
    }

    override fun isClear(section: Pair<String, String>, incoming: Boolean): Boolean =
        queues.isClear(section, incoming).first

    override fun isClear(transport: Transport, incoming: Boolean): Boolean =
        queues.isClear(transport.section(), incoming).first

    override fun isClearWithPriority(section: Pair<String, String>): Pair<Boolean, Int> =
        queues.isClear(section, true)

    override fun isSwitchPlatform(transport: Transport, section: Pair<String, String>, destination: Boolean): Boolean =
        switchService.isSwitchPlatform(transport, section, destination)

    override fun initQueues(key: Pair<String, String>) = queues.initQueues(key)

    override fun diagnostics(transports: List<UUID>?) = diagnostics.dump(queues, transports)

    override fun areSectionsClear(
        transport: Transport,
        lineInstructions: LineInstructions,
        sections: (Pair<String, String>) -> List<Pair<String, String>>
    ): Boolean {
        var isClear = true
        val line = transport.line.name
        val platformToKey = Pair("$line:${lineInstructions.direction}", "$line:${lineInstructions.to.id}")

        outer@ for (key in sections(platformToKey)) {
            if (!queues.isClear(key, true).first) {
                isClear = false
                break@outer
            }
        }
        return isClear
    }

    private suspend fun arrive(transport: Transport) = coroutineScope {
        jobs[transport.id]!!.cancelAndJoin()
        journeyRepo.addJourneyTime(transport.getJourneyTime())
        holdChannels[transport.id]!!.send(transport)
    }

    private suspend fun release(transport: Transport) = coroutineScope {
        val job = launch { transport.signal(signalService.getChannel(transport.section())!!) }

        if (switchService.isSwitchSection(transport))
            launch {
                switchService.switch(transport) { details ->
                    launch { processSwitch(details.first, details.second, job) }
                }
            }

        launch {
            signalService.send(
                transport.platformKey(), SignalMessage(
                    signalValue = SignalValue.GREEN,
                    id = transport.id,
                    key = transport.section()
                )
            )
        }

    }

    private suspend fun processSwitch(transport: Transport, section: Pair<String, String>, job: Job) = coroutineScope {
        queues.getQueue(section).removeFirstOrNull()?.let {
            jobs[it.id]!!.cancelAndJoin()
        }
        job.cancelAndJoin()
        prepareRelease(transport) { t -> launch { transport.signal(signalService.getChannel(t.section())!!) } }

    }

    private suspend fun prepareRelease(transport: Transport, releaseConsumer: Consumer<Transport>) = coroutineScope {
        queues.release(transport.section(), transport)
        jobs[transport.id] = launch { transport.track(queues.getChannel(transport.section())) }
        releaseConsumer.accept(transport)
    }

    companion object {
        private val log = LoggerFactory.getLogger(this.javaClass)
        private val jobs: ConcurrentHashMap<UUID, Job> = ConcurrentHashMap()
        private val holdChannels: ConcurrentHashMap<UUID, Channel<Transport>> = ConcurrentHashMap()

        class Queues(private val minimumHold: Int, private val journeyRepo: JourneyRepo) {
            private val queues: ConcurrentHashMap<Pair<String, String>, Pair<Channel<Transport>, ArrayDeque<Transport>>> =
                ConcurrentHashMap()

            fun getQueue(key: Pair<String, String>): ArrayDeque<Transport> = queues[key]!!.second

            fun initQueues(key: Pair<String, String>) {
                queues[key] = Pair(Channel(), ArrayDeque())
            }
            fun isClear(section: Pair<String, String>, incoming: Boolean): Pair<Boolean, Int> =
                Pair(
                    queues[section]!!.second.isEmpty()
                            || (
                            queues[section]!!.second.size < 2 //only 1 transporter per section currently.
                                    && journeyRepo.getJourneyTime(section, minimumHold + 1).first > minimumHold
                                    && ((!incoming && defaultCheck(section)) || (incoming && incomingCheck(section)))),
                            journeyTimeInSection(section))

            private fun defaultCheck(section: Pair<String, String>) =
                checkDistanceTravelled(
                    section,
                    queues[section]!!.second.last().getPosition(),
                    false
                ) && !queues[section]!!.second.last().isStationary()


            private fun incomingCheck(section: Pair<String, String>) =
                checkDistanceTravelled(
                    section,
                    queues[section]!!.second.first().getPosition(),
                    true
                )

            private fun journeyTimeInSection(section: Pair<String, String>) =
                queues[section]!!.second.lastOrNull()?.getJourneyTime()?.second ?: 0

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
                        log.info("${it.id} current instruction ${it.line.id} ${it.getCurrentInstruction()} in ${it.section()} ${it.getPosition()}")
                    }
                }

                queues.getQueueKeys().forEach { queue ->
                    val toAdd = queues.getQueue(queue)
                        .filter { t -> transports == null || transports.contains(t.id) }
                        .map { m -> m.journal.getLog().sortedByDescending { l -> l.milliseconds }.take(5) }
                        .flatten()
                    items.addAll(toAdd)
                }

                items.sortedByDescending { it.milliseconds }.forEach { log.info(it.print()) }
            }
        }
    }
}