package com.tabiiki.kotlinlab.service

import com.tabiiki.kotlinlab.factory.Origin
import com.tabiiki.kotlinlab.factory.SignalMessage
import com.tabiiki.kotlinlab.factory.SignalValue
import com.tabiiki.kotlinlab.model.Commuter
import com.tabiiki.kotlinlab.model.Line
import com.tabiiki.kotlinlab.model.Transport
import com.tabiiki.kotlinlab.monitor.PlatformMonitor
import com.tabiiki.kotlinlab.repo.LineDirection
import com.tabiiki.kotlinlab.repo.LineInstructions
import com.tabiiki.kotlinlab.repo.LineRepo
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.springframework.stereotype.Service
import java.util.concurrent.atomic.AtomicInteger

@Service
class PlatformService(
    private val signalService: SignalService,
    private val sectionService: SectionService,
    private val lineRepo: LineRepo
) {
    private val platformMonitor = PlatformMonitor(signalService)
    private var commuterChannel: Channel<Commuter>? = null

    init {
        signalService.getSectionSignals().forEach { sectionService.initQueues(it) }
        signalService.getPlatformSignals().forEach { platformMonitor.init(it) }
    }

    fun initCommuterChannel(commuterChannel: Channel<Commuter>) {
        this.commuterChannel = commuterChannel
    }

    suspend fun initLines(line: String, lineDetails: List<Line>): Unit =
        coroutineScope {
            lineRepo.addLineDetails(line, lineDetails)
            launch { sectionService.init(line) { t -> launch { hold(t) } } }

            platformMonitor.getPlatformKeys().filter { it.first.contains(line) }.forEach {
                launch { init(it) }
            }
            signalService.initConnected(line, lineRepo)
        }

    suspend fun buffer(transporters: MutableList<Transport>) = coroutineScope {
        val line = transporters.first().line.name
        println("buffering $line")

        do {
            val first = transporters.first()
            val releasePack = releasePack(first)
            val platformClear = if (releasePack.second) {
                platformMonitor.isClear(
                    Pair(
                        "${first.line.name}:${LineDirection.TERMINAL}",
                        first.platformKey().second
                    )
                )
            } else {
                platformMonitor.isClear(first.platformKey())
            }
            val canRelease = sectionService.isSectionClear(
                transport = first,
                isTerminalSection = releasePack.second
            )

            if (platformClear && canRelease) {
                release(transport = first, releasePack = releasePack)
                transporters.remove(first)
            }

            delay(first.timeStep * 10)
        } while (transporters.isNotEmpty())

        println("$line all dispatched")
    }

    suspend fun release(
        transport: Transport,
        releasePack: Triple<LineInstructions, Boolean, Pair<String, String>>? = null
    ): Unit = coroutineScope {
        println("releasing ${transport.id}")
        signalAndHold(transport = transport, releasePack = releasePack ?: releasePack(transport))
    }

    private fun releasePack(transport: Transport): Triple<LineInstructions, Boolean, Pair<String, String>> {
        val instructions = lineRepo.getLineInstructions(transport, transport.timeStep.toInt() * 50)
        val switchPlatform = sectionService.isSwitchPlatform(transport, transport.section())
        val key = getPlatformSignalKey(transport, instructions, switchPlatform)

        return Triple(instructions, switchPlatform, key)
    }

    private fun platformToKey(transport: Transport): Pair<String, String> {
        var switchStation = false
        if (!transport.platformKey().first.contains(LineDirection.TERMINAL.name)) {
            switchStation = sectionService.isSwitchPlatform(transport, transport.getJourneyTime().first, true)
        }

        return transport.platformToKey(switchStation)!!
    }

    private suspend fun hold(
        transport: Transport
    ): Unit = coroutineScope {
        val platformToKey = platformToKey(transport)
        // get a flavour for this,  then consider a virtual buffer.
        if (!platformMonitor.isClear(platformToKey)) throw RuntimeException("${transport.id} platform not clear $platformToKey owner: ${platformMonitor.getOwner(platformToKey)}")
        signalAndHold(transport = transport, releasePack = releasePack(transport))
    }

    private suspend fun signalAndHold(
        transport: Transport,
        releasePack: Triple<LineInstructions, Boolean, Pair<String, String>>
    ) = coroutineScope {
        signalService.send(
            releasePack.third,
            SignalMessage(
                signalValue = SignalValue.RED,
                id = transport.id,
                key = releasePack.third,
                line = transport.line.id,
                commuterChannel = transport.carriage.channel,
                origin = Origin.RELEASE
            )
        )

        launch {
            holdActions(
                transport = transport,
                key = releasePack.third,
                lineInstructions = releasePack.first,
                switchPlatform = releasePack.second
            )
        }
    }

    private suspend fun holdActions(
        transport: Transport,
        key: Pair<String, String>,
        lineInstructions: LineInstructions,
        switchPlatform: Boolean
    ) = coroutineScope {
        val minimumHold = transport.timeStep.toInt() * 50
        val counter = AtomicInteger(0)
        val embarkJob = launch {
            transport.carriage.embark(commuterChannel!!)
        }
        val disembarkJob = launch {
            transport.carriage.disembark(Line.getStation(key.second), commuterChannel!!)
        }

        var canRelease = false

        do {
            delay(transport.timeStep)
            if ((counter.incrementAndGet() > minimumHold / transport.timeStep)) {
                canRelease = sectionService.isSectionClear(
                    transport = transport,
                    isTerminalSection = switchPlatform
                )
            }
        } while (!canRelease)

        dispatch(
            transport = transport,
            jobs = listOf(embarkJob, disembarkJob),
            lineInstructions = lineInstructions,
            switchPlatform = switchPlatform
        )
    }

    private suspend fun dispatch(
        transport: Transport,
        jobs: List<Job>? = null,
        lineInstructions: LineInstructions,
        switchPlatform: Boolean
    ): Unit = coroutineScope {
        transport.startJourney(lineInstructions = lineInstructions)
        val job = launch { transport.motionLoop() }
        sectionService.accept(
            transport = transport
                .also {
                    if (switchPlatform) {
                        val section = it.section()
                        it.addSwitchSection(Pair("${section.first}|", Line.getStation(section.first)))
                    }
                },
            motionJob = job,
            jobs = jobs
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
