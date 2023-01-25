package com.tabiiki.kotlinlab.service

import com.tabiiki.kotlinlab.factory.LineFactory
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
    fun initCommuterChannel(commuterChannel: Channel<Commuter>)
    suspend fun initLines(line: String, lineDetails: List<Line>)
    suspend fun signalAndDispatch(transport: Transport)
    fun isClear(transport: Transport): Boolean
    fun dump()
}

@Service
class PlatformServiceImpl(
    @Value("\${network.minimum-hold}") private val minimumHold: Int,
    private val signalService: SignalService,
    private val sectionService: SectionService,
    private val lineRepo: LineRepo,
    private val stationRepo: StationRepo,
    private val lineFactory: LineFactory,
) : PlatformService {
    private val platformMonitor = PlatformMonitor(sectionService, signalService)
    private var commuterChannel: Channel<Commuter>? = null

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

        return platformClear && (transport.line.overrideIsClear ||
                !transport.line.overrideIsClear && sectionService.isClear(
            transport = transport,
            switchFrom = switchPlatform,
            approachingJunction = lineFactory.isJunction(
                line = transport.line.name,
                station = transport.section().second
            )
        ))
    }

    override fun dump() {
        platformMonitor.dump()
        sectionService.dump()
    }

    override fun initCommuterChannel(commuterChannel: Channel<Commuter>) {
        this.commuterChannel = commuterChannel
    }

    override suspend fun initLines(line: String, lineDetails: List<Line>): Unit =
        coroutineScope {
            lineRepo.addLineDetails(line, lineDetails)
            launch { sectionService.init(line) }

            platformMonitor.getPlatformKeys().filter { it.first.contains(line) }.forEach {
                launch { init(it) }
                launch { platformMonitor.monitorPlatformHold(it) { t -> launch { hold(t) } } }
            }
            signalService.initConnected(line, lineRepo)
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
            holdActions(
                transport = transport,
                key = key,
                lineInstructions = instructions,
                switchPlatform = switchPlatform,
            )
        }
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
        val embarkJob = launch {
            transport.carriage.embark(commuterChannel!!)
        }
        val disembarkJob = launch {
            transport.carriage.disembark(Line.getStation(key.second), commuterChannel!!)
        }

        val holdLogger = AtomicBoolean(false)

        var canRelease: Triple<Boolean, Boolean, Boolean>

        do {
            delay(transport.timeStep)

            canRelease = platformConductor(
                transport = transport,
                switchPlatform = switchPlatform,
                key = key,
                lineInstructions = lineInstructions
            )

            if (counter.get() > minimumHold * 3 && !holdLogger.get())
                holdLogger.set(true).also {
                    println("holding ${transport.id} at $key data: $canRelease")
                }

        } while (counter.incrementAndGet() < minimumHold
            || !canRelease.first
            || !canRelease.second
            || !canRelease.third
        )

        if (holdLogger.get())
            println("released ${transport.id} from hold")

        dispatch(
            transport = transport,
            jobs = listOf(embarkJob, disembarkJob),
            lineInstructions = lineInstructions,
            switchPlatform = switchPlatform
        )
    }

    private fun platformConductor(
        transport: Transport,
        switchPlatform: Boolean,
        lineInstructions: LineInstructions,
        key: Pair<String, String>
    ): Triple<Boolean, Boolean, Boolean> =
        Triple(sectionService.isClear(
            transport = transport,
            switchFrom = switchPlatform,
            approachingJunction = lineFactory.isJunction(
                line = transport.line.name,
                station = lineInstructions.to.id
            )
        ),
            sectionService.arePreviousSectionsClear(
                transport = transport,
                lineInstructions = lineInstructions
            ) { k -> lineRepo.getPreviousSections(k) }, !switchPlatform || platformMonitor.isClear(
                key = Pair(
                    "${transport.line.name}:${
                        transport.lineDirection(true)
                    }", key.second
                )
            ))

    private suspend fun dispatch(
        transport: Transport,
        jobs: List<Job>? = null,
        lineInstructions: LineInstructions,
        switchPlatform: Boolean
    ): Unit = coroutineScope {
        transport.startJourney(lineInstructions = lineInstructions)
        val job = launch { transport.motionLoop() }
        //TESTING - should send a GREEN to relevant section
        sectionService.accept(
            transport = transport
                .also {
                    if (switchPlatform) {
                        val section = it.section()
                        it.addSwitchSection(Pair("${section.first}|", Line.getStation(section.first)))
                    }
                    it.setHoldChannel(platformMonitor.getHoldChannel(it))
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