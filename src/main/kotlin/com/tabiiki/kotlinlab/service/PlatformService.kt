package com.tabiiki.kotlinlab.service

import com.tabiiki.kotlinlab.factory.SignalMessage
import com.tabiiki.kotlinlab.factory.SignalValue
import com.tabiiki.kotlinlab.model.Line
import com.tabiiki.kotlinlab.model.Transport
import com.tabiiki.kotlinlab.repo.LineDirection
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
        var response = true
        val line = transport.section().first.substringBefore(":")
        val stations = stationRepo.getPreviousStationsOnLine(
            lineRepo.getLineStations(line),
            transport.getSectionStationCode(),
            transport.lineDirection()
        )
        outer@ for (station in stations) {
            val toTest = Pair("$line:${station.id}", transport.getSectionStationCode())
            if (!sectionService.isClear(toTest, false)) {
                response = false
                break@outer
            }
        }
        return response
    }

    override fun diagnostics(transports: List<UUID>) {
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
        //TODO this fixes the city line for now
        if (lineRepo.getLineStations(transport).size != 2) {
            launch {
                signalService.send(
                    key, SignalMessage(
                        signalValue = SignalValue.RED,
                        id = Optional.of(transport.id),
                        key = Optional.of(transport.section())
                    )
                )
            }
        }

        val counter = AtomicInteger(0)
        do {
            delay(transport.timeStep)
        } while (counter.incrementAndGet() < minimumHold || !areSectionsClear(transport, lineInstructions))

        dispatch(transport, lineInstructions, Optional.of(key))
    }


    override suspend fun release(
        transport: Transport
    ): Unit = coroutineScope {
        val instructions = lineRepo.getLineInstructions(transport)
        dispatch(transport, instructions, Optional.empty())
    }

    private suspend fun dispatch(
        transport: Transport,
        instructions: LineInstructions,
        key: Optional<Pair<String, String>>
    ) = coroutineScope {
        val actualKey = key.orElse(platformKey(transport, instructions))
        launch { transport.release(instructions) }
        launch { addToSection(actualKey, transport) }
        platforms.release(actualKey, transport)
    }

    private fun areSectionsClear(transport: Transport, lineInstructions: LineInstructions): Boolean {
        var response = true
        val line = transport.line.name
        val platformToKey = Pair("$line:${lineInstructions.direction}", "$line:${lineInstructions.to.id}")

        outer@ for (key in getPreviousSections(platformToKey)) {
            if (!sectionService.isClear(key)) {
                response = false
                break@outer
            }
        }

        return response
    }

    private fun platformKey(transport: Transport, instructions: LineInstructions): Pair<String, String> {
        val line = transport.line.name
        val dir = instructions.direction
        return Pair("$line:$dir", transport.section().first)
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
        launch { sectionService.add(transport, holdChannels[transport.platformToKey().get()]!!) }
    }

    private suspend fun monitorPlatformHold(key: Pair<String, String>) = coroutineScope {
        val channel = Channel<Transport>()
        holdChannels[key] = channel
        do {
            val msg = channel.receive()
            msg.platformToKey().ifPresent {
                if (!platforms.atPlatform(it).isEmpty) {
                    throw RuntimeException(
                        "$it already has holding ${
                            platforms.atPlatform(it).get().id
                        }, arrived too quickly"
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
                        getPreviousSections(key).forEach {
                            launch {
                                signalService.send(
                                    it,
                                    SignalMessage(signalValue = SignalValue.RED, key = signal.key, id = signal.id)
                                )
                            }
                        }

                    SignalValue.GREEN -> {
                        val sections = getPreviousSections(key).map { Pair(it, sectionService.isClear(it)) }

                        sections.filter { it.second }.forEach { section ->
                            launch {
                                signalService.send(
                                    section.first,
                                    SignalMessage(signalValue = SignalValue.GREEN, key = signal.key)
                                )
                            }
                        }

                        sections.firstOrNull { !it.second }?.let {
                            launch {
                                signalService.send(
                                    it.first,
                                    SignalMessage(signalValue = SignalValue.GREEN, key = signal.key)
                                )
                            }
                        }
                    }

                    else -> {}
                }
            }

        } while (true)
    }

    private fun getPreviousSections(platformKey: Pair<String, String>): List<Pair<String, String>> {
        val line = platformKey.first.substringBefore(":")
        val direction = platformKey.first.substringAfter(":")
        val stationTo = platformKey.second.substringAfter(":")
        val stationsFrom = stationRepo.getPreviousStationsOnLine(
            lineRepo.getLineStations(line),
            stationTo,
            LineDirection.valueOf(direction)
        )

        return stationsFrom.map { Pair("$line:${it.id}", stationTo) }.distinct()
    }

    companion object {
        private val log = LoggerFactory.getLogger(this.javaClass)
        private val holdChannels: ConcurrentHashMap<Pair<String, String>, Channel<Transport>> = ConcurrentHashMap()

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
                    throw RuntimeException("FATAL - $key ${transport.id}")
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

            fun dump(platforms: Platforms, transports: List<UUID>) {
                val items = mutableListOf<Transport.Companion.JournalRecord>()

                platforms.getPlatformKeys().forEach { queue ->
                    val toAdd = platforms.atPlatform(queue)
                        .filter { t -> transports.contains(t.id) }
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