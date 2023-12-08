package com.tabiiki.kotlinlab.service

import com.tabiiki.kotlinlab.factory.LineFactory
import com.tabiiki.kotlinlab.factory.Origin
import com.tabiiki.kotlinlab.factory.SignalMessage
import com.tabiiki.kotlinlab.factory.SignalValue
import com.tabiiki.kotlinlab.model.Commuter
import com.tabiiki.kotlinlab.model.Line
import com.tabiiki.kotlinlab.model.Transport
import com.tabiiki.kotlinlab.monitor.PlatformMonitorV2
import com.tabiiki.kotlinlab.repo.LineDirection
import com.tabiiki.kotlinlab.repo.LineRepo
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.springframework.stereotype.Service
import java.util.concurrent.atomic.AtomicInteger

enum class PlatformSignalType {
    ENTRY, EXIT
}

@Service
class PlatformServiceV2(
    private val sectionService: SectionServiceV2,
    private val signalService: SignalService,
    private val lineFactory: LineFactory,
    private val lineRepo: LineRepo
) {
    private val platformMonitorV2 = PlatformMonitorV2(signalService)
    private var commuterChannel: Channel<Commuter>? = null

    suspend fun init(commuterChannel: Channel<Commuter>) = coroutineScope {
        this@PlatformServiceV2.commuterChannel = commuterChannel
        signalService.getPlatformSignals().forEach {
            launch { signalService.init(it) }
            launch {
                platformMonitorV2.monitor(it)
            }
        }

        lineFactory.get().map { lineFactory.get(it) }.groupBy { it.name }.values.forEach { line ->
            lineRepo.addLineDetails(line.first().name, line)
        }
        lineFactory.get().map { lineFactory.get(it).name }.distinct().forEach {
            signalService.initConnected(line = it, lineRepo = lineRepo)
        }

        launch { sectionService.init() }
    }

    suspend fun release(transport: Transport, jobs: List<Job> = emptyList()) = coroutineScope {
        val isSwitchPlatform = sectionService.isSwitchPlatform(transport, transport.section())
        val platformSignalEntryKey = transport.platformKey().getPlatformEntryKey(switchPlatform = isSwitchPlatform)
        val platformSignalExitKey = transport.platformKey().getPlatformExitKey(switchPlatform = isSwitchPlatform)

        transport.startJourney(
            lineInstructions = lineRepo.getLineInstructions(
                transport = transport,
                minimumHold = transport.timeStep.toInt() * 50
            )
        )
        signalService.send(
            key = platformSignalExitKey,
            signalMessage = SignalMessage(
                signalValue = SignalValue.RED,
                id = transport.id,
                line = transport.line.id,
                origin = Origin.RELEASE
            )
        )

        if (!isSwitchPlatform) {
            signalService.send(
                key = platformSignalEntryKey,
                signalMessage = SignalMessage(
                    signalValue = SignalValue.GREEN,
                    id = transport.id,
                    line = transport.line.id,
                    origin = Origin.RELEASE
                )
            )
        }

        val transportArrivedChannel = Channel<Transport>()
        launch {
            monitorTransportArrivedChannel(channel = transportArrivedChannel)
        }

        val job = launch {
            transport.motionLoop(channel = transportArrivedChannel) {
                //sectionService.removeFromQueue(key = transport.section())
                launch {
                    signalService.send(
                        key = platformSignalExitKey,
                        signalMessage = SignalMessage(
                            signalValue = SignalValue.GREEN,
                            id = it.id,
                            line = it.line.id,
                            origin = Origin.HOLD
                        )
                    )
                }
            }
        }

        sectionService.accept(
            transport = transport
                .also {
                    if (isSwitchPlatform) {
                        val section = it.section()
                        it.addSwitchSection(Pair("${section.first}|", Line.getStation(section.first)))
                    }
                },
            motionJob = job,
            jobs = jobs
        )
    }

    suspend fun release(transporters: MutableList<Transport>) = coroutineScope {
        transporters.groupBy { it.section().first }.forEach { (_, u) ->
            launch { releaseBySection(transporters = u.toMutableList()) }
        }
     }

    suspend fun hold(transport: Transport) = coroutineScope {
        val platformSignalEntryKey = transport.platformKey().getPlatformEntryKey(
            switchPlatform = sectionService.isSwitchPlatform(transport, transport.section())
        )
        val platformSignalExitKey = transport.platformKey().getPlatformExitKey(
            switchPlatform = sectionService.isSwitchPlatform(transport, transport.section())
        )

        val embarkJob = launch {
            transport.carriage.embark(commuterChannel!!)
        }
        val disembarkJob = launch {
            transport.carriage.disembark(Line.getStation(transport.platformKey().second), commuterChannel!!)
        }

        signalService.send(
            key = platformSignalEntryKey,
            signalMessage = SignalMessage(
                signalValue = SignalValue.RED,
                id = transport.id,
                line = transport.line.id,
                origin = Origin.HOLD
            )
        )
        var canRelease: Boolean
        val minimumHold = transport.timeStep.toInt() * 50
        val counter = AtomicInteger(0)

        do {
            delay(transport.timeStep)
            canRelease = (counter.incrementAndGet() > minimumHold / transport.timeStep)
            if (canRelease) {
                canRelease = signalService.receive(platformSignalExitKey)?.signalValue == SignalValue.GREEN
            }
        } while (!canRelease)

        launch { release(transport = transport, jobs = listOf(embarkJob, disembarkJob)) }
    }

    private suspend fun monitorTransportArrivedChannel(channel: Channel<Transport>) {
        val msg = channel.receive()
        channel.close()
        hold(transport = msg)
    }

    private suspend fun releaseBySection(transporters: MutableList<Transport>) = coroutineScope {
        val line = transporters.first().line.id
        // println("releasing by section ${transporters.first().section()}")
        do {
            val transport = transporters.removeFirst()
            val startedAt = System.currentTimeMillis()
            val switchPlatform = sectionService.isSwitchPlatform(transport, transport.section())
            val entryKey = transport.platformKey().getPlatformEntryKey(switchPlatform)
            val exitKey = transport.platformKey().getPlatformExitKey(switchPlatform)

            var release: Boolean
            do {
                val platformEntrySignal = signalService.receive(entryKey)
                val platformExitSignal = signalService.receive(exitKey)

                release =
                    platformEntrySignal?.signalValue == SignalValue.GREEN &&
                            platformExitSignal?.signalValue == SignalValue.GREEN &&
                            platformExitSignal.timesStamp > startedAt &&
                            platformEntrySignal.timesStamp > startedAt
            } while (!release)

            release(transport = transport)
            delay(transport.timeStep)
        } while (transporters.isNotEmpty())

        println("buffered network release completed for $line")
    }

    companion object {
        fun Pair<String, String>.getPlatformExitKey(switchPlatform: Boolean): Pair<String, String> =
            when (switchPlatform) {
                true -> Pair("${this.first.substringBefore(":")}:${LineDirection.TERMINAL}", this.second)
                false -> Pair("${this.first}:${PlatformSignalType.EXIT}", this.second)
            }
        fun Pair<String, String>.getPlatformEntryKey(switchPlatform: Boolean): Pair<String, String> =
            when (switchPlatform) {
                true -> Pair("${this.first.substringBefore(":")}:${LineDirection.TERMINAL}", this.second)
                false -> Pair("${this.first}:${PlatformSignalType.ENTRY}", this.second)
            }
    }
}
