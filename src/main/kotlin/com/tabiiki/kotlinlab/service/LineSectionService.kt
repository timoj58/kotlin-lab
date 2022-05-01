package com.tabiiki.kotlinlab.service

import com.tabiiki.kotlinlab.factory.SignalValue
import com.tabiiki.kotlinlab.model.Line
import com.tabiiki.kotlinlab.model.Station
import com.tabiiki.kotlinlab.model.Transport
import com.tabiiki.kotlinlab.repo.StationRepo
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

enum class LineDirection {
    POSITIVE, NEGATIVE
}

data class LineInstructions(val from: Station, val to: Station, val next: Station, val direction: LineDirection)

interface LineSectionService {
    suspend fun start(line: String, lines: List<Line>)
    suspend fun release(transport: Transport)
    fun clear(key: Pair<String, String>): Boolean
}

@Service
class LineSectionServiceImpl(
    private val signalService: SignalService,
    stationRepo: StationRepo
) : LineSectionService {
    private val queues = Queues()
    private val channels = Channels()
    private val lines = Lines(stationRepo)
    private val diagnostics = Diagnostics()
    private val jobs: ConcurrentHashMap<UUID, Job> = ConcurrentHashMap()

    init {
        signalService.getSectionSignals().forEach { queues.initSectionQueues(it) }
        signalService.getPlatformSignals().forEach { queues.initPlatformQueues(it) }
    }

    override suspend fun start(line: String, lineDetails: List<Line>): Unit = coroutineScope {
        queues.getPlatformQueueKeys().forEach {
            launch { initPlatform(it) }
        }
        queues.getSectionQueueKeys().forEach {
            launch { initSection(it) }
        }

        lines.addLineDetails(line, lineDetails)
    }

    override suspend fun release(
        transport: Transport
    ): Unit = coroutineScope {
        val instructions = lines.lineInstructions(transport, lines.getLineStations(transport))

        launch { transport.release(instructions) }

        val line = transport.line.name
        val dir = instructions.direction
        val key = Pair("$line $dir", transport.section().first)
        queues.platformRelease(key, transport)
    }

   //TODO move at some point.
    private suspend fun hold(
        transport: Transport
    ): Unit = coroutineScope {
        val counter = AtomicInteger(0)
        do {
            delay(transport.timeStep)
            if (counter.incrementAndGet() > 100) {
                diagnostics.diagnostics(queues, transport)
                throw RuntimeException("${transport.id} being held indefinitely ${transport.platformKey()}")
            }
        } while (counter.get() < 45 || !clear(transport.platformKey()))

        release(transport)
    }

    override fun clear(key: Pair<String, String>): Boolean = queues.clear(key)

    private suspend fun initPlatform(key: Pair<String, String>) = coroutineScope {
        launch { initSignals(key) }
        launch { monitorPlatformChannel(key) }
        launch { monitorPlatformSignal(key) }
    }

    private suspend fun initSection(key: Pair<String, String>) = coroutineScope {
        launch { initSignals(key) }
        launch { monitorSectionChannel(key, queues.getSectionChannel(key)) }
    }

    private suspend fun initSignals(key: Pair<String, String>) = coroutineScope {
        val channelIn = channels.initIn(key)
        val channelOut = channels.initOut(key)

        launch { signalService.start(key, channelIn, channelOut) }
    }

    private suspend fun addToLineSection(
        key: Pair<String, String>,
        transport: Transport,
        channel: Channel<SignalValue>
    ) = coroutineScope {
        launch {
            do {
                delay(transport.timeStep)
            } while (queues.getPlatformQueue(key).contains(transport))
            queues.sectionRelease(transport.section(), transport)
            jobs[transport.id] = launch { transport.track(queues.getSectionChannel(transport.section())) }
            launch { transport.signal(channel) }
        }
    }

    suspend fun monitorPlatformSignal(key: Pair<String, String>) = coroutineScope {
        do {
            channels.receive(key)?.let { queues.sendToPlatformQueue(key, it) }
        } while (true)
    }

    private suspend fun monitorPlatformChannel(key: Pair<String, String>) = coroutineScope {
        val channel = queues.getPlatformChannel(key)

        var previousSignal: SignalValue? = null
        var lock = AtomicBoolean(false)
        do {
            val signal = channel.receive()
            if (previousSignal == null || signal != previousSignal) {
                lock.set(false)
            }
            previousSignal = signal
            if (!lock.get()) {
                when (signal) {
                    SignalValue.GREEN ->
                        queues.getPlatformQueue(key).firstOrNull()?.let {
                            channels.getChannel(it.section())?.let { channel ->
                                lock.set(true)
                                queues.getPlatformQueue(key).removeFirstOrNull()?.let { transport ->
                                    launch {
                                        channels.send(key, SignalValue.RED)
                                    }
                                    launch {
                                        addToLineSection(key, transport, channel)
                                    }
                                }
                            }
                        }
                    else -> {}
                }
            }

        } while (true)
    }

    private suspend fun monitorSectionChannel(key: Pair<String, String>, channel: Channel<Transport>) = coroutineScope {
        do {
            val msg = channel.receive()
            when (msg.atPlatform()) {
                true -> {
                    queues.getSectionQueue(key).removeFirstOrNull()?.let {
                        jobs[it.id]!!.cancelAndJoin()
                        val platformFromKey = msg.platformFromKey()
                        launch { channels.send(platformFromKey, SignalValue.GREEN) }
                        launch { hold(it) }

                        it.journal.add(
                            Transport.Companion.JournalRecord(
                                action = Transport.Companion.JournalActions.ARRIVE_SECTION, key = key
                            )
                        )
                    }
                }
                else -> {}
            }
        } while (true)
    }

    companion object {
        private val log = LoggerFactory.getLogger(this.javaClass)

        class Queues {
            //should only allow one train per queue.  would help identify errors.  (until more platforms).
            private val platformQueues: ConcurrentHashMap<Pair<String, String>, Pair<Channel<SignalValue>, ArrayDeque<Transport>>> =
                ConcurrentHashMap()
            private val sectionQueues: ConcurrentHashMap<Pair<String, String>, Pair<Channel<Transport>, ArrayDeque<Transport>>> =
                ConcurrentHashMap()

            fun initPlatformQueues(key: Pair<String, String>) {
                platformQueues[key] = Pair(Channel(), ArrayDeque())
            }

            fun initSectionQueues(key: Pair<String, String>) {
                sectionQueues[key] = Pair(Channel(), ArrayDeque())
            }

            fun getPlatformQueueKeys(): Iterator<Pair<String, String>> = platformQueues.keys().asIterator()
            fun getSectionQueueKeys(): Iterator<Pair<String, String>> = sectionQueues.keys().asIterator()

            fun platformRelease(key: Pair<String, String>, transport: Transport) {
                if (!platformQueues[key]!!.second.isEmpty()) throw RuntimeException("FATAL - $key")

                platformQueues[key]!!.second.add(transport)
                transport.journal.add(
                    Transport.Companion.JournalRecord(
                        action = Transport.Companion.JournalActions.PLATFORM, key = key
                    )
                )
            }

            fun sectionRelease(key: Pair<String, String>, transport: Transport) {
                sectionQueues[key]!!.second.add(transport)
                transport.journal.add(
                    Transport.Companion.JournalRecord(
                        action = Transport.Companion.JournalActions.RELEASE, key = key
                    )
                )
            }

            suspend fun sendToPlatformQueue(key: Pair<String, String>, signalValue: SignalValue) {
                platformQueues[key]!!.first.send(signalValue)
            }

            fun getPlatformChannel(key: Pair<String, String>): Channel<SignalValue> = platformQueues[key]!!.first
            fun getSectionChannel(key: Pair<String, String>): Channel<Transport> = sectionQueues[key]!!.first
            fun getPlatformQueue(key: Pair<String, String>): ArrayDeque<Transport> =
                platformQueues[key]!!.second

            fun getSectionQueue(key: Pair<String, String>): ArrayDeque<Transport> =
                sectionQueues[key]!!.second

            fun clear(key: Pair<String, String>): Boolean = platformQueues[key]?.second?.isEmpty() ?: false
        }

        class Channels {
            private val channelsIn: ConcurrentHashMap<Pair<String, String>, Channel<SignalValue>> = ConcurrentHashMap()
            private val channelsOut: ConcurrentHashMap<Pair<String, String>, Channel<SignalValue>> = ConcurrentHashMap()

            fun initIn(key: Pair<String, String>): Channel<SignalValue> {
                channelsIn[key] = Channel()
                return channelsIn[key]!!
            }

            fun initOut(key: Pair<String, String>): Channel<SignalValue> {
                channelsOut[key] = Channel()
                return channelsOut[key]!!
            }

            suspend fun send(key: Pair<String, String>, signalValue: SignalValue) {
                channelsIn[key]!!.send(signalValue)
            }

            suspend fun receive(key: Pair<String, String>): SignalValue? = channelsOut[key]?.receive()
            fun getChannel(key: Pair<String, String>): Channel<SignalValue>? = channelsOut[key]

        }

        class Lines(private val stationRepo: StationRepo) {
            private val lineDetails: ConcurrentHashMap<String, List<Line>> = ConcurrentHashMap()

            fun addLineDetails(key: String, details: List<Line>) {
                lineDetails[key] = details
            }

            fun lineInstructions(transport: Transport, lineStations: List<String>): LineInstructions =
                LineInstructions(
                    from = stationRepo.get(transport.section().first),
                    to = stationRepo.get(transport.section().second),
                    next = stationRepo.getNextStationOnLine(
                        lineStations = lineStations, section = transport.section()
                    ),
                    direction = transport.lineDirection()
                )

            //need to memoize this ideally.  TODO.
            fun getLineStations(transport: Transport) =
                lineDetails[transport.line.name]!!.first { l -> l.transporters.any { it.id == transport.id } }.stations

        }

        class Diagnostics {
            fun diagnostics(queues: Queues, transport: Transport) {
                transportDiagnostics(queues, transport)
                signalDiagnostics(queues, transport)
            }

            private fun transportDiagnostics(queues: Queues, transport: Transport) {
                log.warn("ALL DIAGNOSTICS")
                var journals = mutableListOf<Transport.Companion.JournalRecord>()
                journals.addAll(transport.journal.getLog())
                queues.getSectionQueueKeys().forEach {
                    queues.getSectionQueue(it).forEach { t ->
                        journals.addAll(t.journal.getLog())
                    }
                }

                journals.sortedBy { it.milliseconds }.forEach {
                    log.info(it.print())
                }
            }

            private fun signalDiagnostics(queues: Queues, transport: Transport) {
                log.warn("waiting: ${transport.id}")
                val platformKey = transport.platformKey()
                var lineSection: Pair<String, String>? = null
                queues.getPlatformQueue(platformKey).forEach {
                    log.warn("platform key: $platformKey - ${it.id}")
                    it.journal.getLog().sortedByDescending { s -> s.milliseconds }.forEach { l ->
                        log.warn(l.print())
                    }
                    lineSection = it.section()
                }

                lineSection?.let { ls ->
                    queues.getSectionQueue(ls).forEach {
                        log.warn("section key: $ls - ${it.id}")
                        it.journal.getLog().sortedByDescending { s -> s.milliseconds }.forEach { l ->
                            log.warn(l.print())
                        }
                    }
                }

            }

        }

    }

}