package com.tabiiki.kotlinlab.service

import com.tabiiki.kotlinlab.factory.SignalMessageV2
import com.tabiiki.kotlinlab.factory.SignalV2
import com.tabiiki.kotlinlab.factory.SignalValue
import com.tabiiki.kotlinlab.model.Commuter
import com.tabiiki.kotlinlab.model.Line
import com.tabiiki.kotlinlab.model.Transport
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Consumer

enum class PlatformSignalType {
    ENTRY, EXIT
}

@Service
class PlatformServiceV2(
    private val sectionService: SectionServiceV2,
    private val signalService: SignalServiceV2,
    private val lineService: LineService
) {
    private val platformLocks: ConcurrentHashMap<Pair<String, String>, AtomicBoolean> = ConcurrentHashMap()
    private var commuterChannel: Channel<Commuter>? = null

    fun getPlatformSignals(): List<SignalV2> = signalService.getPlatformSignals()

    suspend fun init(
        commuterChannel: Channel<Commuter>
    ) = coroutineScope {
        this@PlatformServiceV2.commuterChannel = commuterChannel

        signalService.init(
            lines = lineService.getLines(),
            isSwitchStation = { l, s -> lineService.isSwitchStation(l, s) },
            previousSections = { k -> lineService.getPreviousSections(k) }
        )

        signalService.getPlatformSignals().forEach {
            platformLocks[it.key] = AtomicBoolean(false)
        }

        launch { signalService.monitor() }
    }

    suspend fun subscribeStations(
        stationSubscribers: Map<Pair<String, String>, Channel<SignalMessageV2>>
    ) = coroutineScope {
        stationSubscribers.forEach {
            launch {
                signalService.subscribe(
                    key = it.key,
                    channel = it.value
                )
            }
        }
    }

    suspend fun release(transport: Transport, jobs: List<Job> = emptyList()) = coroutineScope {
        if (jobs.isEmpty()) println("releasing ${transport.id} ${transport.section()}")
        val isSwitchPlatform = sectionService.isSwitchPlatform(transport, transport.section())
        val platformSignalEntryKey = transport.platformKey().getPlatformEntryKey(switchPlatform = isSwitchPlatform)
        val platformSignalExitKey = transport.platformKey().getPlatformExitKey(switchPlatform = isSwitchPlatform)

        transport.startJourney(
            lineInstructions = lineService.getLineInstructions(
                transport = transport,
                minimumHold = transport.timeStep.toInt() * 50
            )
        )
        launch {
            signalService.send(
                key = platformSignalExitKey,
                message = SignalMessageV2(
                    signalValue = SignalValue.RED,
                    line = transport.line.id,
                    key = platformSignalExitKey,
                    id = transport.id
                )
            )
        }

        if (!isSwitchPlatform) {
            launch {
                signalService.send(
                    key = platformSignalEntryKey,
                    message = SignalMessageV2(
                        signalValue = SignalValue.GREEN,
                        line = transport.line.id,
                        key = platformSignalEntryKey,
                        id = transport.id
                    )
                )
            }
        }

        val transportArrivedChannel = Channel<Transport>()

        launch {
            monitorTransportArrivedChannel(channel = transportArrivedChannel) {
                launch {
                    signalService.send(
                        key = platformSignalExitKey,
                        message = SignalMessageV2(
                            signalValue = SignalValue.GREEN,
                            line = it.line.id,
                            id = transport.id,
                            key = platformSignalExitKey
                        )
                    )
                }
            }
        }

        val job = launch { transport.motionLoop(arrivalChannel = transportArrivedChannel) }
        val sectionSubscription = Channel<SignalMessageV2>()
        launch {
            signalService.subscribe(
                key = transport.section(),
                channel = sectionSubscription
            )
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
            jobs = jobs,
            sectionSubscription = sectionSubscription,
            arrivalChannel = transportArrivedChannel
        ) {
            launch {
                val sectionLeft = it.second
                val sectionEntering = it.third
                val transporter = it.first

                val subscriber = Channel<SignalMessageV2>()

                launch {
                    signalService.subscribe(
                        key = sectionEntering,
                        channel = subscriber
                    )
                }

                launch {
                    transporter.monitorSectionSignal(subscriber) { t ->
                        launch {
                            if (sectionEntering.second.contains("|")) {
                                SignalMessageV2(
                                    signalValue = SignalValue.GREEN,
                                    id = t.id,
                                    key = sectionLeft,
                                    line = t.line.id
                                ).also {
                                    launch {
                                        signalService.send(
                                            key = t.getMainlineForSwitch(),
                                            message = it
                                        )
                                    }

                                    launch {
                                        signalService.send(
                                            key = t.section(),
                                            message = it
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
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
        // if(platformLocks[platformSignalEntryKey]!!.get()) throw RuntimeException("${transport.id} $platformSignalEntryKey is locked")
        platformLocks[platformSignalEntryKey]!!.set(true)
        val embarkChannel = Channel<Commuter>()
        val embarkJob = launch {
            transport.carriage.embark(
                embarkChannel = embarkChannel,
                commuterChannel = commuterChannel!!
            )
        }
        val disembarkJob = launch {
            transport.carriage.disembark(Line.getStation(transport.platformKey().second), commuterChannel!!)
        }

        launch {
            signalService.send(
                key = platformSignalEntryKey,
                message = SignalMessageV2(
                    signalValue = SignalValue.RED,
                    line = transport.line.id,
                    key = platformSignalEntryKey,
                    id = transport.id,
                    commuterChannel = embarkChannel
                )
            )
        }

        val subscriber = Channel<SignalMessageV2>()

        launch {
            signalService.subscribe(
                key = platformSignalExitKey,
                channel = subscriber,
                emit = true
            )
        }

        var canRelease: Boolean
        val minimumHold = transport.timeStep.toInt() * 50
        val counter = AtomicInteger(0)

        do {
            delay(transport.timeStep)
            canRelease = (counter.incrementAndGet() > minimumHold / transport.timeStep)
            if (canRelease) {
                canRelease = subscriber.receive().signalValue == SignalValue.GREEN
            }
        } while (!canRelease)

        subscriber.close()
        platformLocks[platformSignalEntryKey]!!.set(false)
        launch { release(transport = transport, jobs = listOf(embarkJob, disembarkJob)) }
    }

    private suspend fun monitorTransportArrivedChannel(
        channel: Channel<Transport>,
        arrivalAction: Consumer<Transport>
    ) {
        val msg = channel.receive()
        arrivalAction.accept(msg)
        channel.close()
        hold(transport = msg)
    }

    private suspend fun releaseBySection(transporters: MutableList<Transport>) = coroutineScope {
        val section = transporters.first().section()
        do {
            val transport = transporters.removeFirst()
            val switchPlatform = sectionService.isSwitchPlatform(transport, transport.section())
            val entryKey = transport.platformKey().getPlatformEntryKey(switchPlatform)
            val exitKey = transport.platformKey().getPlatformExitKey(switchPlatform)
            val entrySignalSubscription = Channel<SignalMessageV2>()
            val exitSignalSubscription = Channel<SignalMessageV2>()

            launch { signalService.subscribe(key = entryKey, channel = entrySignalSubscription, emit = true) }
            launch { signalService.subscribe(key = exitKey, channel = exitSignalSubscription, emit = true) }

            var release: Boolean
            do {
                val platformEntrySignal = entrySignalSubscription.receive()
                val platformExitSignal = exitSignalSubscription.receive()

                release =
                    platformEntrySignal.signalValue == SignalValue.GREEN &&
                    platformExitSignal.signalValue == SignalValue.GREEN
            } while (!release)
            entrySignalSubscription.close()
            exitSignalSubscription.close()
            launch { hold(transport = transport) }
            delay(transport.timeStep * 50)
        } while (transporters.isNotEmpty())

        println("buffered network release completed for $section")
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
