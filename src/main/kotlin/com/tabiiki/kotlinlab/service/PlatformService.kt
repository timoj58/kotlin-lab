package com.tabiiki.kotlinlab.service

import com.tabiiki.kotlinlab.factory.SignalMessage
import com.tabiiki.kotlinlab.factory.SignalValue
import com.tabiiki.kotlinlab.model.Line
import com.tabiiki.kotlinlab.model.Transport
import com.tabiiki.kotlinlab.repo.LineRepo
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

interface PlatformService {
    suspend fun start(line: String, lineDetails: List<Line>)
    suspend fun release(transport: Transport)
    fun isClear(transport: Transport): Boolean
    fun diagnostics(transports: List<UUID>)
}

@Service
class PlatformServiceImpl(
    @Value("\${network.minimum-hold}") private val minimumHold: Int,
    private val signalService: SignalService,
    private val sectionService: SectionService,
    private val lineRepo: LineRepo,
    private val stationRepo: StationRepo
) : PlatformService {
    private val queues = Queues()
    private val diagnostics = Diagnostics()

    init {
        signalService.getSectionSignals().forEach { sectionService.initQueues(it) }
        signalService.getPlatformSignals().forEach { queues.initQueues(it) }
    }

    override fun isClear(transport: Transport): Boolean =
        queues.isClear(transport.platformKey()) && sectionService.isClear(transport.section())

    override fun diagnostics(transports: List<UUID>) {
        diagnostics.dump(queues, transports)
        sectionService.diagnostics(transports)
    }

    override suspend fun start(line: String, lineDetails: List<Line>): Unit = coroutineScope {
        lineRepo.addLineDetails(line, lineDetails)

        launch { sectionService.init(line) }

        queues.getQueueKeys().filter { it.first.contains(line) }.forEach {
            launch { init(it) }
            launch { monitorPlatformHold(it) }
        }
    }

    override suspend fun release(
        transport: Transport
    ): Unit = coroutineScope {
        val instructions = lineRepo.getLineInstructions(transport)

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

    private suspend fun hold(
        transport: Transport
    ): Unit = coroutineScope {
        signalService.send(getPreviousSection(transport), SignalMessage(signalValue = SignalValue.AMBER_30))

        val counter = AtomicInteger(0)
        do {
            delay(transport.timeStep)
            //  if(counter.get() > 75) throw RuntimeException("${transport.id} is held too long")
        } while (counter.incrementAndGet() < minimumHold || !isClear(transport))

        launch { release(transport) }
    }

    private suspend fun addToSection(
        key: Pair<String, String>,
        transport: Transport
    ) = coroutineScope {
        do {
            delay(transport.timeStep)
        } while (queues.getQueue(key).contains(transport))

        signalService.send(getPreviousSection(transport), SignalMessage(signalValue = SignalValue.GREEN))
        sectionService.add(transport, holdChannels[transport.platformToKey()]!!)
    }

    private suspend fun monitorPlatformHold(key: Pair<String, String>) {
        val channel = Channel<Transport>()
        holdChannels[key] = channel

        do {
            hold(channel.receive())
        } while (true)
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
                    else -> {}
                }
            }

        } while (true)
    }

    private fun getPreviousSection(transport: Transport): Pair<String, String> {
        val stationTo = transport.getSectionStationCode()
        val stationFrom =  stationRepo.getPreviousStationOnLine(lineRepo.getLineStations(transport), transport.section()).id

        return Pair("${transport.line.name}:${stationFrom}", stationTo)
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

            fun getQueueKeys(): List<Pair<String, String>> = queues.keys().toList()

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


        class Diagnostics {

            fun dump(queues: Queues, transports: List<UUID>) {
                val items = mutableListOf<Transport.Companion.JournalRecord>()

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