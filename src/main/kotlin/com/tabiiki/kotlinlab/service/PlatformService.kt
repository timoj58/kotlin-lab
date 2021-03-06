package com.tabiiki.kotlinlab.service

import com.tabiiki.kotlinlab.factory.SignalMessage
import com.tabiiki.kotlinlab.factory.SignalValue
import com.tabiiki.kotlinlab.model.Line
import com.tabiiki.kotlinlab.model.Transport
import com.tabiiki.kotlinlab.monitor.PlatformMonitor
import com.tabiiki.kotlinlab.repo.LineDirection
import com.tabiiki.kotlinlab.repo.LineInstructions
import com.tabiiki.kotlinlab.repo.LineRepo
import com.tabiiki.kotlinlab.repo.StationRepo
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

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
    private val stationRepo: StationRepo,
) : PlatformService {
    private val diagnostics = Diagnostics()
    private val platformMonitor = PlatformMonitor(sectionService, signalService, lineRepo)

    init {
        signalService.getSectionSignals().forEach { sectionService.initQueues(it) }
        signalService.getPlatformSignals().forEach { platformMonitor.init(it) }
    }

    override fun isClear(transport: Transport): Boolean {
        val switchPlatform = sectionService.isSwitchPlatform(transport, transport.section())
        val platformClear = if (switchPlatform)
            platformMonitor.isClear(
                Pair(
                    "${transport.line.name}:${LineDirection.TERMINAL}",
                    transport.platformKey().second
                )
            )
        else platformMonitor.isClear(transport.platformKey())

        return platformClear && sectionService.isClear(transport)
    }

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
        //   diagnostics.dump(platforms, transports)
        //   sectionService.diagnostics(transports)
    }

    override suspend fun start(line: String, lineDetails: List<Line>): Unit = coroutineScope {
        lineRepo.addLineDetails(line, lineDetails)
        launch { sectionService.init(line) }

        platformMonitor.getPlatformKeys().filter { it.first.contains(line) }.forEach {
            launch { init(it) }
            launch { platformMonitor.monitorPlatformHold(it) { t -> launch { hold(t) } } }
        }
    }

    override suspend fun hold(
        transport: Transport
    ): Unit = coroutineScope {
        val lineInstructions = lineRepo.getLineInstructions(transport)
        var key = platformKey(transport, lineInstructions)
        if (sectionService.isSwitchPlatform(transport, transport.section()))
            key = platformTerminalKey(transport, key)

        transport.journal.add(
            Transport.Companion.JournalRecord(
                action = Transport.Companion.JournalActions.PLATFORM_HOLD,
                key = key,
                signal = SignalValue.RED
            )
        )
        platformMonitor.accept(key, transport)
        //TODO this fixes the city line for now, and Romford.  3 or less
        if (lineRepo.getLineStations(transport).size > 3) {
            launch {
                signalService.send(
                    key, SignalMessage(
                        signalValue = SignalValue.RED,
                        id = transport.id,
                        key = key
                    )
                )
            }
        }

        val counter = AtomicInteger(0)
        do {
            delay(transport.timeStep)
        } while (counter.incrementAndGet() < minimumHold
            || !sectionService.isClear(transport)
            || !sectionService.areSectionsClear(transport, lineInstructions) { k -> lineRepo.getPreviousSections(k) }
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
        var actualKey = key ?: platformKey(transport, instructions)
        actualKey = testForDepartureTerminal(transport, actualKey)

        launch { transport.release(instructions) }
        launch { addToSection(actualKey, transport) }

        platformMonitor.release(actualKey, transport)
    }

    private fun testForDepartureTerminal(transport: Transport, key: Pair<String, String>): Pair<String, String> {
        if (sectionService.isSwitchPlatform(transport, transport.section())) {
            transport.addSwitchSection(
                Pair(
                    "${transport.section().first}|",
                    transport.section().first.substringAfter(":")
                )
            )
            return platformTerminalKey(transport, key)
        }
        return key
    }

    private suspend fun init(key: Pair<String, String>) = coroutineScope {
        launch { signalService.init(key) }
        launch { platformMonitor.monitorPlatform(key) }
    }

    private suspend fun addToSection(
        key: Pair<String, String>,
        transport: Transport
    ) = coroutineScope {
        do {
            delay(transport.timeStep)
        } while (!platformMonitor.atPlatform(key).isEmpty)

        val holdChannelKey = platformMonitor.platformToKey(transport)
        launch { sectionService.accept(transport, platformMonitor.getHoldChannel(holdChannelKey)) }
    }

    companion object {
        private val log = LoggerFactory.getLogger(this.javaClass)

        private fun platformKey(transport: Transport, instructions: LineInstructions): Pair<String, String> {
            val line = transport.line.name
            val dir = instructions.direction
            return Pair("$line:$dir", transport.section().first)
        }

        private fun platformTerminalKey(transport: Transport, key: Pair<String, String>): Pair<String, String> =
            Pair("${transport.line.name}:${LineDirection.TERMINAL}", key.second.substringBefore("|"))

        class Diagnostics {

            fun dump(platformMonitor: PlatformMonitor, transports: List<UUID>?) {
                val items = mutableListOf<Transport.Companion.JournalRecord>()

                platformMonitor.getPlatformKeys().forEach { queue ->
                    platformMonitor.atPlatform(queue).ifPresent {
                        log.info("${it.id} current instruction ${it.line.id} ${it.getCurrentInstruction()} in $queue")
                    }
                }
                platformMonitor.getPlatformKeys().forEach { queue ->
                    val toAdd = platformMonitor.atPlatform(queue)
                        .filter { t -> transports == null || transports.contains(t.id) }
                        .map { m -> m.journal.getLog().sortedByDescending { l -> l.milliseconds }.take(5) }
                    toAdd.ifPresent {
                        items.addAll(it)
                    }
                }

                items.sortedByDescending { it.milliseconds }.forEach { log.info(it.print()) }
            }
        }
    }
}