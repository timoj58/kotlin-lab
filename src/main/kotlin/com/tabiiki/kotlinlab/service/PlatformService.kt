package com.tabiiki.kotlinlab.service

import com.tabiiki.kotlinlab.factory.SignalMessage
import com.tabiiki.kotlinlab.factory.SignalValue
import com.tabiiki.kotlinlab.model.Commuter
import com.tabiiki.kotlinlab.model.Line
import com.tabiiki.kotlinlab.model.Transport
import com.tabiiki.kotlinlab.monitor.PlatformMonitor
import com.tabiiki.kotlinlab.repo.LineDirection
import com.tabiiki.kotlinlab.repo.LineInstructions
import com.tabiiki.kotlinlab.repo.LineRepo
import com.tabiiki.kotlinlab.repo.StationRepo
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

interface PlatformService {
    fun init(commuterChannel: Channel<Commuter>)
    suspend fun init(line: String, lineDetails: List<Line>)
    suspend fun signalAndDispatch(transport: Transport)
    fun isClear(transport: Transport): Boolean
    fun canLaunch(transport: Transport): Boolean
}

@Service
class PlatformServiceImpl(
    @Value("\${network.minimum-hold}") private val minimumHold: Int,
    private val signalService: SignalService,
    private val sectionService: SectionService,
    private val lineRepo: LineRepo,
    private val stationRepo: StationRepo,
) : PlatformService {
    private val platformMonitor = PlatformMonitor(sectionService, signalService, lineRepo)
    private var commuterChannel: Channel<Commuter>? = null

    init {
        signalService.getSectionSignals().forEach { sectionService.initQueues(it) }
        signalService.getPlatformSignals().forEach { platformMonitor.init(it) }
    }

    override fun isClear(transport: Transport): Boolean {
        val switchPlatform = sectionService.isSwitchPlatform(transport, transport.section())
        //val switchSection = sectionService.isSwitchSection(transport)
        val platformClear = if (switchPlatform)
            platformMonitor.isClear(
                Pair(
                    "${transport.line.name}:${LineDirection.TERMINAL}",
                    transport.platformKey().second
                )
            )
        else platformMonitor.isClear(transport.platformKey())

        return platformClear && sectionService.isClear(
            transport = transport,
            switchFrom = switchPlatform,
            //switchTo = switchSection
        )
    }

    override fun canLaunch(transport: Transport): Boolean {
        var response = true
        val line = Line.getLine(transport.section().first)
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

    override fun init(commuterChannel: Channel<Commuter>) {
        this.commuterChannel = commuterChannel
    }

    override suspend fun init(line: String, lineDetails: List<Line>): Unit =
        coroutineScope {
            lineRepo.addLineDetails(line, lineDetails)
            launch { sectionService.init(line) }

            platformMonitor.getPlatformKeys().filter { it.first.contains(line) }.forEach {
                launch { init(it) }
                launch { platformMonitor.monitorPlatformHold(it) { t -> launch { hold(t) } } }
            }
        }

    override suspend fun signalAndDispatch(transport: Transport): Unit = coroutineScope {
        val instructions = lineRepo.getLineInstructions(transport)
        val switchPlatform = sectionService.isSwitchPlatform(transport, transport.section())
        val key = getPlatformSignalKey(transport, instructions, switchPlatform)

        signalService.send(
            key, SignalMessage(
                signalValue = SignalValue.RED,
                id = transport.id,
                key = key,
                line = transport.line.id,
                commuterChannel = transport.carriage.channel,
            )
        )

        launch {
            val counter = AtomicInteger(0)
            do {
                delay(transport.timeStep)
            } while (counter.incrementAndGet() < minimumHold)

            dispatch(
                transport = transport,
                lineInstructions = instructions,
                switchPlatform = switchPlatform,
            )
        }
    }

    private suspend fun dispatch(
        transport: Transport,
        jobs: List<Job>? = null,
        lineInstructions: LineInstructions,
        switchPlatform: Boolean
    ): Unit = coroutineScope {
        transport.startJourney(lineInstructions = lineInstructions)
        launch { transport.motionLoop() }
        sectionService.accept(
            transport
                .also {
                    if (switchPlatform) {
                        val section = it.section()
                        it.addSwitchSection(Pair("${section.first}|", Line.getStation(section.first)))
                    }
                    it.setHoldChannel(platformMonitor.getHoldChannel(it)) }, jobs
        )
    }

    private suspend fun hold(
        transport: Transport
    ): Unit = coroutineScope {
        val lineInstructions = lineRepo.getLineInstructions(transport)
        val switchPlatform = sectionService.isSwitchPlatform(transport, transport.section())
        val key = getPlatformSignalKey(transport, lineInstructions, switchPlatform)

        signalService.send(
            key, SignalMessage(
                signalValue = SignalValue.RED,
                id = transport.id,
                key = key,
                line = transport.line.id,
                commuterChannel = transport.carriage.channel,
            )
        )

        delay(transport.timeStep)

        launch {
            holdActions(
                transport = transport,
                key = key,
                lineInstructions = lineInstructions,
                switchPlatform = switchPlatform,
            )
        }

    }

    private suspend fun holdActions(
        transport: Transport,
        key: Pair<String, String>,
        lineInstructions: LineInstructions,
        switchPlatform: Boolean
    ) = coroutineScope {
        val counter = AtomicInteger(0)
        val embarkJob = launch { transport.carriage.embark(commuterChannel!!) }
        val disembarkJob = launch { transport.carriage.disembark(Line.getStation(key.second), commuterChannel!!) }
       // val switchSection = sectionService.isSwitchSection(transport)

        var sectionClear: Boolean
        var sectionsClear: Boolean
        var isOtherPlatformClear: Boolean

        do {
            delay(transport.timeStep)

            sectionClear = sectionService.isClear(transport = transport, switchFrom = switchPlatform/*, switchTo = switchSection*/)
            sectionsClear = sectionService.areSectionsClear(transport = transport, lineInstructions =  lineInstructions) { k -> lineRepo.getPreviousSections(k) }
            isOtherPlatformClear =  !switchPlatform ||  platformMonitor.isClear(key = Pair("${transport.line.name}:${transport.lineDirection(true)}", key.second))

        } while (counter.incrementAndGet() < minimumHold
            || !sectionClear
            || !sectionsClear
            || !isOtherPlatformClear)

        dispatch(
            transport = transport,
            jobs = listOf(embarkJob, disembarkJob),
            lineInstructions = lineInstructions,
            switchPlatform = switchPlatform
        )
    }

    private suspend fun init(key: Pair<String, String>) = coroutineScope {
        launch { signalService.init(key) }
        launch { platformMonitor.monitorPlatform(key) }
    }

    private fun getPlatformSignalKey(
        transport: Transport,
        lineInstructions: LineInstructions,
        switchPlatform: Boolean
    ): Pair<String, String> {
        var key = platformKey(transport, lineInstructions)
        if (switchPlatform) key = platformTerminalKey(transport, key)

        return key
    }

    companion object {
        private fun platformKey(transport: Transport, instructions: LineInstructions): Pair<String, String> {
            val line = transport.line.name
            val dir = instructions.direction
            return Pair("$line:$dir", transport.section().first)
        }

        private fun platformTerminalKey(transport: Transport, key: Pair<String, String>): Pair<String, String> =
            Pair("${transport.line.name}:${LineDirection.TERMINAL}", key.second.substringBefore("|"))
    }
}