package com.tabiiki.kotlinlab.service

import com.tabiiki.kotlinlab.factory.SignalMessage
import com.tabiiki.kotlinlab.factory.SignalValue
import com.tabiiki.kotlinlab.model.Line
import com.tabiiki.kotlinlab.model.Station
import com.tabiiki.kotlinlab.model.Transport
import com.tabiiki.kotlinlab.repo.JourneyRepo
import com.tabiiki.kotlinlab.repo.StationRepo
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

enum class LineDirection {
    POSITIVE, NEGATIVE
}

data class LineInstructions(
    val from: Station,
    val to: Station,
    val next: Station,
    val direction: LineDirection,
    val minimumHold: Int = 45
)

interface LineService {
    suspend fun start(line: String, lines: List<Line>)
    suspend fun release(transport: Transport)
    fun isClear(transport: Transport): Boolean
    fun dump()
}

@Service
class LineServiceImpl(
    @Value("\${network.minimum-hold}") private val minimumHold: Int,
    private val signalService: SignalService,
    stationRepo: StationRepo,
    private val sectionService: SectionService
) : LineService {
    private val queues = Queues()
    private val diagnostics = Diagnostics()
    private val lines = Lines(stationRepo)

    init {
        signalService.getSectionSignals().forEach { sectionService.initQueues(it) }
        signalService.getPlatformSignals().forEach { queues.initQueues(it) }
    }

    override fun isClear(transport: Transport): Boolean =
        queues.isClear(transport.platformKey()) && sectionService.isClear(transport.section())

    override fun dump() {
        diagnostics.dump(queues)
        sectionService.dump()
    }

    override suspend fun start(line: String, lineDetails: List<Line>): Unit = coroutineScope {
        queues.getQueueKeys().forEach {
            launch { init(it) }
            launch { monitorPlatformHold(it) }
        }
        sectionService.getQueueKeys().forEach {
            launch { sectionService.init(it) }
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

        queues.release(key, transport)
    }

    private suspend fun init(key: Pair<String, String>) = coroutineScope {
        launch { signalService.init(key) }
        launch { monitorPlatformChannel(key) }
        launch { monitorPlatformSignal(key) }
    }

    private suspend fun monitorPlatformHold(key: Pair<String, String>){
        val channel = Channel<Transport>()
        holdChannels[key] = channel

        do {
            hold(channel.receive())
        }while (true)
    }

    private suspend fun hold(
        transport: Transport
    ): Unit = coroutineScope {
        val counter = AtomicInteger(0)
        do {
            delay(transport.timeStep)
            counter.incrementAndGet()
        } while (counter.get() < minimumHold || !isClear(transport))

        release(transport)
    }

    private suspend fun addToSection(
        key: Pair<String, String>,
        transport: Transport
    ) = coroutineScope {
        do {
            delay(transport.timeStep)
        } while (queues.getQueue(key).contains(transport))

        sectionService.add(transport, holdChannels[transport.platformToKey()]!!)
    }


    private suspend fun monitorPlatformSignal(key: Pair<String, String>) = coroutineScope {
        do {
            signalService.receive(key)?.let { queues.sendToQueue(key, it) }
        } while (true)
    }

    private suspend fun monitorPlatformChannel(key: Pair<String, String>) = coroutineScope {
        val channel = queues.getChannel(key)

        var previousSignal: SignalValue? = null
        var lock = AtomicBoolean(false)
        do {
            val signal = channel.receive()
            if (previousSignal == null || signal.signalValue != previousSignal) {
                lock.set(false)
            }
            previousSignal = signal.signalValue
            if (!lock.get()) {
                when (signal.signalValue) {
                    SignalValue.GREEN ->
                        queues.getQueue(key).firstOrNull()?.let {
                            signalService.getChannel(it.section())?.let { channel ->
                                lock.set(true)
                                queues.getQueue(key).removeFirstOrNull()?.let { transport ->
                                    launch { signalService.send(key, SignalMessage(SignalValue.RED)) }
                                    launch { addToSection(key, transport) }
                                }
                            }
                        }
                   //TODO this is better SignalValue.RED -> queues.getQueue(key).firstOrNull()?.let { launch { hold(it) } }
                    else -> {}
                }
            }

        } while (true)
    }

    companion object {
        private val log = LoggerFactory.getLogger(this.javaClass)
        private val holdChannels: ConcurrentHashMap<Pair<String, String>, Channel<Transport>> = ConcurrentHashMap()

        class Queues {
            private val queues: ConcurrentHashMap<Pair<String, String>, Pair<Channel<SignalMessage>, ArrayDeque<Transport>>> =
                ConcurrentHashMap()

            fun initQueues(key: Pair<String, String>) {
                queues[key] = Pair(Channel(), ArrayDeque())
            }

            fun isClear(key: Pair<String, String>): Boolean = queues[key]?.second?.isEmpty() ?: false

            fun getQueueKeys(): Iterator<Pair<String, String>> = queues.keys().asIterator()

            fun release(key: Pair<String, String>, transport: Transport) {
                if (!queues[key]!!.second.isEmpty()) throw RuntimeException("FATAL - $key")

                queues[key]!!.second.add(transport)
                transport.journal.add(
                    Transport.Companion.JournalRecord(
                        action = Transport.Companion.JournalActions.READY_TO_DEPART, key = key, signal = SignalValue.RED
                    )
                )
            }

            suspend fun sendToQueue(key: Pair<String, String>, signalMessage: SignalMessage) {
                queues[key]!!.first.send(signalMessage)
            }

            fun getChannel(key: Pair<String, String>): Channel<SignalMessage> = queues[key]!!.first
            fun getQueue(key: Pair<String, String>): ArrayDeque<Transport> = queues[key]!!.second
        }

        class Lines(private val stationRepo: StationRepo) {

            private val lineDetails: ConcurrentHashMap<String, List<Line>> = ConcurrentHashMap()
            private val lineStations: ConcurrentHashMap<UUID, List<String>> = ConcurrentHashMap()

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

            fun getLineStations(transport: Transport): List<String> {
                if (!lineStations.contains(transport.id))
                    lineStations[transport.id] =
                        lineDetails[transport.line.name]!!.first { l -> l.transporters.any { it.id == transport.id } }.stations
                return lineStations[transport.id]!!
            }
        }

        class Diagnostics {

            fun dump(queues: Queues) {
                val items = mutableListOf<Transport>()

                queues.getQueueKeys().forEach {
                    items.addAll(queues.getQueue(it))
                }

                items.map { it.journal.getLog() }.flatten().sortedBy { it.milliseconds }
                    .forEach { log.info(it.print()) }
            }
        }
    }

}