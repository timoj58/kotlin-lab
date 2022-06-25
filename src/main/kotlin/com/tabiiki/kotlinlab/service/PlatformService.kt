package com.tabiiki.kotlinlab.service

import com.tabiiki.kotlinlab.factory.SignalMessage
import com.tabiiki.kotlinlab.factory.SignalValue
import com.tabiiki.kotlinlab.model.Line
import com.tabiiki.kotlinlab.model.Transport
import com.tabiiki.kotlinlab.repo.LineInstructions
import com.tabiiki.kotlinlab.repo.LineRepo
import com.tabiiki.kotlinlab.repo.StationRepo
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.util.Optional
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

interface PlatformService {
    suspend fun start(line: String, lineDetails: List<Line>)
    suspend fun hold(transport: Transport)
    suspend fun release(transport: Transport)
    fun isClear(transport: Transport): Boolean
    fun canLaunch(transport: Transport): Boolean
    fun diagnostics(transports: List<UUID>?)
}

@Service
class PlatformServiceImpl(
    @Value("\${network.minimum-hold}") private val minimumHold: Int,
    private val signalService: SignalService,
    private val sectionService: SectionService,
    private val lineRepo: LineRepo,
    private val stationRepo: StationRepo
) : PlatformService {
    private val platforms = Platforms()
    private val diagnostics = Diagnostics()

    init {
        signalService.getSectionSignals().forEach { sectionService.initQueues(it) }
        signalService.getPlatformSignals().forEach {
            platforms.init(it)
        }
    }

    override fun isClear(transport: Transport): Boolean =
        platforms.isClear(transport.platformKey()) && sectionService.isClear(transport.section())

    override fun canLaunch(transport: Transport): Boolean {
        if (lineRepo.getLineStations(transport).size == 2) return true

        var response = true
        val line = transport.section().first.substringBefore(":")
        val stations = stationRepo.getPreviousStationsOnLine(
            lineRepo.getLineStations(line),
            transport.getSectionStationCode(),
            transport.lineDirection()
        )
        outer@ for (station in stations) {
            val toTest = Pair("$line:${station.id}", transport.getSectionStationCode())
            if (!sectionService.isClear(toTest, true)) {
                response = false
                break@outer
            }
        }
        return response
    }

    override fun diagnostics(transports: List<UUID>?) {
        diagnostics.dump(platforms, transports)
        sectionService.diagnostics(transports)
    }

    override suspend fun start(line: String, lineDetails: List<Line>): Unit = coroutineScope {
        lineRepo.addLineDetails(line, lineDetails)
        launch { sectionService.init(line) }

        platforms.getPlatformKeys().filter { it.first.contains(line) }.forEach {
            launch { init(it) }
            launch { monitorPlatformHold(it) }
        }
    }

    override suspend fun hold(
        transport: Transport
    ): Unit = coroutineScope {
        val lineInstructions = lineRepo.getLineInstructions(transport)
        val key = platformKey(transport, lineInstructions)

        platforms.add(key, transport)
        //TODO this fixes the city line for now, and Romford.  3 or less
        if (lineRepo.getLineStations(transport).size > 3) {
            launch {
                signalService.send(
                    key, SignalMessage(
                        signalValue = SignalValue.RED,
                        id = transport.id,
                        key = transport.section()
                    )
                )
            }
        }

        val counter = AtomicInteger(0)
        do {
            delay(transport.timeStep)
        } while (counter.incrementAndGet() < minimumHold
            || !sectionService.isClear(transport.section())
            || !sectionService.areSectionsClear(transport, lineInstructions)
        )

        dispatch(transport, lineInstructions, key)
    }

    override suspend fun release(
        transport: Transport
    ): Unit = coroutineScope {
        val instructions = lineRepo.getLineInstructions(transport)
        dispatch(transport, instructions, null)
    }

    private suspend fun dispatch(
        transport: Transport,
        instructions: LineInstructions,
        key: Pair<String, String>?
    ) = coroutineScope {
        val actualKey = key ?: platformKey(transport, instructions)
        launch { transport.release(instructions) }
        launch { addToSection(actualKey, transport) }
        platforms.release(actualKey, transport)
    }

    private suspend fun init(key: Pair<String, String>) = coroutineScope {
        launch { signalService.init(key) }
        launch { monitorPlatformChannel(key) }
        launch { monitorPlatformSignal(key) }
    }

    private suspend fun addToSection(
        key: Pair<String, String>,
        transport: Transport
    ) = coroutineScope {
        do {
            delay(transport.timeStep)
        } while (!platforms.atPlatform(key).isEmpty)
        launch { sectionService.add(transport, holdChannels[transport.platformToKey()!!]!!) }
    }

    private suspend fun monitorPlatformHold(key: Pair<String, String>) = coroutineScope {
        val channel = Channel<Transport>()
        holdChannels[key] = channel
        do {
            val msg = channel.receive()
            msg.platformToKey()?.let {
                if (!platforms.atPlatform(it).isEmpty) {
                    val transporter = platforms.atPlatform(it).get()
                    diagnostics(null)
                    throw RuntimeException(
                        "${msg.id} arrived too quickly $it , already holding ${transporter.id} "
                    )
                }
            }
            launch { hold(msg) }
        } while (true)
    }

    private suspend fun monitorPlatformSignal(key: Pair<String, String>) = coroutineScope {
        do {
            signalService.receive(key)?.let { platforms.sendToPlatform(key, it) }
        } while (true)
    }

    private suspend fun monitorPlatformChannel(key: Pair<String, String>) = coroutineScope {
        val channel = platforms.getChannel(key)
        var previousSignal: SignalMessage? = null
        do {
            val signal = channel.receive()
            if (previousSignal == null || signal != previousSignal) {
                previousSignal = signal
                when (signal.signalValue) {
                    SignalValue.RED ->
                        lineRepo.getPreviousSections(key).forEach {
                            launch {
                                signalService.send(
                                    it,
                                    SignalMessage(signalValue = SignalValue.RED, key = signal.key, id = signal.id)
                                )
                            }
                        }

                    SignalValue.GREEN -> {
                        val sections =
                            lineRepo.getPreviousSections(key).map { Pair(it, sectionService.isClearWithPriority(it)) }
                        val priority = sections.sortedByDescending { it.second.second }.firstOrNull { !it.second.first }
                        priority?.let {
                            launch {
                                signalService.send(
                                    it.first,
                                    SignalMessage(signalValue = SignalValue.GREEN, key = signal.key)
                                )
                            }
                        }

                        if (priority == null) {
                            sections.filter { it.second.first }.forEach { section ->
                                launch {
                                    signalService.send(
                                        section.first,
                                        SignalMessage(signalValue = SignalValue.GREEN, key = signal.key)
                                    )
                                }
                            }
                        }
                    }
                }
            }

        } while (true)
    }

    companion object {
        private val log = LoggerFactory.getLogger(this.javaClass)
        private val holdChannels: ConcurrentHashMap<Pair<String, String>, Channel<Transport>> = ConcurrentHashMap()

        private fun platformKey(transport: Transport, instructions: LineInstructions): Pair<String, String> {
            val line = transport.line.name
            val dir = instructions.direction
            return Pair("$line:$dir", transport.section().first)
        }

        class Platforms {
            private val platforms: ConcurrentHashMap<Pair<String, String>, Pair<Channel<SignalMessage>, AtomicReference<Optional<Transport>>>> =
                ConcurrentHashMap()

            fun init(key: Pair<String, String>) {
                platforms[key] = Pair(Channel(), AtomicReference(Optional.empty()))
            }

            fun isClear(key: Pair<String, String>): Boolean =
                platforms[key]?.second?.get()?.isEmpty ?: true

            fun getPlatformKeys(): List<Pair<String, String>> = platforms.keys().toList()

            fun add(key: Pair<String, String>, transport: Transport) {
                if (!platforms[key]!!.second.get().isEmpty) {
                    throw RuntimeException(
                        "FATAL - already holding ${
                            platforms[key]!!.second.get().get().id
                        } for $key next ${transport.id}"
                    )
                }
                platforms[key]!!.second.set(Optional.of(transport))
            }

            fun release(key: Pair<String, String>, transport: Transport) {
                platforms[key]!!.second.set(Optional.empty())
                transport.journal.add(
                    Transport.Companion.JournalRecord(
                        action = Transport.Companion.JournalActions.READY_TO_DEPART, key = key, signal = SignalValue.RED
                    )
                )
            }

            suspend fun sendToPlatform(key: Pair<String, String>, signalMessage: SignalMessage) {
                platforms[key]!!.first.send(signalMessage)
            }

            fun getChannel(key: Pair<String, String>): Channel<SignalMessage> = platforms[key]!!.first
            fun atPlatform(key: Pair<String, String>): Optional<Transport> = platforms[key]!!.second.get()
        }

        class Diagnostics {

            fun dump(platforms: Platforms, transports: List<UUID>?) {
                val items = mutableListOf<Transport.Companion.JournalRecord>()

                platforms.getPlatformKeys().forEach { queue ->
                    val toAdd = platforms.atPlatform(queue)
                        .filter { t -> transports == null || transports.contains(t.id) }
                        .map { m -> m.journal.getLog().sortedBy { l -> l.milliseconds }.takeLast(5) }
                    toAdd.ifPresent {
                        items.addAll(it)
                    }
                }

                items.sortedBy { it.milliseconds }
                    .forEach { log.info(it.print()) }
            }
        }
    }
}