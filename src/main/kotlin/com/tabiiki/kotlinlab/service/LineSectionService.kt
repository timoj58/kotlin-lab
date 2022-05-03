package com.tabiiki.kotlinlab.service

import com.tabiiki.kotlinlab.factory.SignalMessage
import com.tabiiki.kotlinlab.factory.SignalValue
import com.tabiiki.kotlinlab.model.Line
import com.tabiiki.kotlinlab.model.Station
import com.tabiiki.kotlinlab.model.Status
import com.tabiiki.kotlinlab.model.Transport
import com.tabiiki.kotlinlab.repo.JourneyRepo
import com.tabiiki.kotlinlab.repo.StationRepo
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.util.Optional
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

enum class LineDirection {
    POSITIVE, NEGATIVE
}

data class LineInstructions(val from: Station, val to: Station, val next: Station, val direction: LineDirection, val minimumHold: Int = 45)

interface LineSectionService {
    suspend fun start(line: String, lines: List<Line>)
    suspend fun release(transport: Transport)
    fun isClear(transport: Transport): Boolean
}

@Service
class LineSectionServiceImpl(
    @Value("\${network.minimum-hold}") private val minimumHold: Int,
    private val signalService: SignalService,
    private val journeyRepo: JourneyRepo,
    stationRepo: StationRepo
) : LineSectionService {
    private val queues = Queues(minimumHold)
    private val channels = Channels()
    private val lines = Lines(stationRepo)
    private val jobs: ConcurrentHashMap<UUID, Job> = ConcurrentHashMap()

    init {
        signalService.getSectionSignals().forEach { queues.initSectionQueues(it) }
        signalService.getPlatformSignals().forEach { queues.initPlatformQueues(it) }
    }

    override fun isClear(transport: Transport): Boolean =
        queues.isPlatformClear(transport.platformKey()) && queues.isSectionClear(transport.section())

    override suspend fun start(line: String, lineDetails: List<Line>): Unit = coroutineScope {
        queues.getPlatformQueueKeys().forEach {
            launch { initPlatform(it) }
        }
        queues.getSectionQueueKeys().forEach {
            launch { initSection(it) }
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
        queues.platformRelease(key, transport)
    }

    private suspend fun initPlatform(key: Pair<String, String>) = coroutineScope {
        launch { initSignals(key) }
        launch { monitorPlatformChannel(key) }
        launch { monitorPlatformSignal(key) }
    }

    private suspend fun initSection(key: Pair<String, String>) = coroutineScope {
        launch { initSignals(key) }
        launch { monitorSectionChannel(key, queues.getSectionChannel(key)) }
    }

    private suspend fun initSignals(key: Pair<String, String>) = coroutineScope {
        val channelIn = channels.initIn(key)
        val channelOut = channels.initOut(key)

        launch { signalService.start(key, channelIn, channelOut) }
    }

    private suspend fun hold(
        transport: Transport
    ): Unit = coroutineScope {
        journeyRepo.addJourneyTime(transport.getJourneyTime())
        val counter = AtomicInteger(0)
        do {
            delay(transport.timeStep)
            counter.incrementAndGet()
        } while (counter.get() < minimumHold || !isClear(transport))

        release(transport)
    }

    private suspend fun addToLineSection(
        key: Pair<String, String>,
        transport: Transport,
        channel: Channel<SignalMessage>
    ) = coroutineScope {
        launch {
            do {
                delay(transport.timeStep)
            } while (queues.getPlatformQueue(key).contains(transport))
            queues.sectionRelease(transport.section(), transport)
            jobs[transport.id] = launch { transport.track(queues.getSectionChannel(transport.section())) }
            launch { transport.signal(channel) }
        }
    }


    private suspend fun monitorPlatformSignal(key: Pair<String, String>) = coroutineScope {
        do {
            channels.receive(key)?.let { queues.sendToPlatformQueue(key, it) }
        } while (true)
    }

    private suspend fun monitorPlatformChannel(key: Pair<String, String>) = coroutineScope {
        val channel = queues.getPlatformChannel(key)

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
                    SignalValue.GREEN, SignalValue.AMBER_10, SignalValue.AMBER_20, SignalValue.AMBER_30 ->
                        queues.getPlatformQueue(key).firstOrNull()?.let {
                            channels.getChannel(it.section())?.let { channel ->
                                lock.set(true)
                                queues.getPlatformQueue(key).removeFirstOrNull()?.let { transport ->
                                 /*   launch {
                                        channels.send(it.section(), SignalMessage(signal.signalValue, Optional.of(it.id)))
                                    } */
                                    launch {
                                        channels.send(key, SignalMessage(SignalValue.RED))
                                    }
                                    launch {
                                        addToLineSection(key, transport, channel)
                                    }
                                }
                            }
                        }
                    else -> {}
                }
            }

        } while (true)
    }

    private suspend fun monitorSectionChannel(key: Pair<String, String>, channel: Channel<Transport>) = coroutineScope {

        do {
            val msg = channel.receive()
            when (msg.atPlatform()) {
                true -> {
                    queues.getSectionQueue(key).removeFirstOrNull()?.let {
                        jobs[it.id]!!.cancelAndJoin()
                        val platformFromKey = msg.platformFromKey()
                        val platformSignalValue = when(queues.getSectionQueue(it.section()).size){
                            0 -> SignalValue.GREEN
                            1 -> SignalValue.AMBER_30
                            2 -> SignalValue.AMBER_20
                            else -> SignalValue.AMBER_10
                        }
                        val sectionSignalValue = when(queues.getSectionQueue(it.section()).size){
                            1 -> SignalValue.GREEN
                            2 -> SignalValue.AMBER_30
                            3 -> SignalValue.AMBER_20
                            else -> SignalValue.AMBER_10
                        }
                        launch { channels.send(platformFromKey,SignalMessage(platformSignalValue)) }
                        //launch { channels.send(it.section(), SignalMessage((sectionSignalValue))) }
                        launch { hold(it) }

                        it.journal.add(
                            Transport.Companion.JournalRecord(
                                action = Transport.Companion.JournalActions.ARRIVE_SECTION, key = key
                            )
                        )
                    }
                }
                else -> {}
            }

        } while (true)
    }

    companion object {
        class Queues(
            private val minimumHold: Int
        ) {
            private val platformQueues: ConcurrentHashMap<Pair<String, String>, Pair<Channel<SignalMessage>, ArrayDeque<Transport>>> =
                ConcurrentHashMap()
            private val sectionQueues: ConcurrentHashMap<Pair<String, String>, Pair<Channel<Transport>, ArrayDeque<Transport>>> =
                ConcurrentHashMap()

            fun initPlatformQueues(key: Pair<String, String>) {
                platformQueues[key] = Pair(Channel(), ArrayDeque())
            }

            fun initSectionQueues(key: Pair<String, String>) {
                sectionQueues[key] = Pair(Channel(), ArrayDeque())
            }

            fun getPlatformQueueKeys(): Iterator<Pair<String, String>> = platformQueues.keys().asIterator()
            fun getSectionQueueKeys(): Iterator<Pair<String, String>> = sectionQueues.keys().asIterator()

            fun platformRelease(key: Pair<String, String>, transport: Transport) {
                if (!platformQueues[key]!!.second.isEmpty()) throw RuntimeException("FATAL - $key")

                platformQueues[key]!!.second.add(transport)
                transport.journal.add(
                    Transport.Companion.JournalRecord(
                        action = Transport.Companion.JournalActions.PLATFORM, key = key
                    )
                )
            }

            fun sectionRelease(key: Pair<String, String>, transport: Transport) {
                sectionQueues[key]!!.second.add(transport)
                transport.journal.add(
                    Transport.Companion.JournalRecord(
                        action = Transport.Companion.JournalActions.RELEASE, key = key
                    )
                )
            }

            suspend fun sendToPlatformQueue(key: Pair<String, String>, signalMessage: SignalMessage) {
                platformQueues[key]!!.first.send(signalMessage)
            }

            fun getPlatformChannel(key: Pair<String, String>): Channel<SignalMessage> = platformQueues[key]!!.first
            fun getSectionChannel(key: Pair<String, String>): Channel<Transport> = sectionQueues[key]!!.first
            fun getPlatformQueue(key: Pair<String, String>): ArrayDeque<Transport> =
                platformQueues[key]!!.second

            fun getSectionQueue(key: Pair<String, String>): ArrayDeque<Transport> =
                sectionQueues[key]!!.second

            fun isPlatformClear(key: Pair<String, String>): Boolean = platformQueues[key]?.second?.isEmpty() ?: false

            fun isSectionClear(key: Pair<String, String>): Boolean {
                if(!sectionQueues.containsKey(key)) return false
                if(sectionQueues[key]!!.second.isEmpty()) return true
                return sectionQueues[key]!!.second.first().getJourneyTime().second > minimumHold
            }
        }

        class Channels {
            private val channelsIn: ConcurrentHashMap<Pair<String, String>, Channel<SignalMessage>> = ConcurrentHashMap()
            private val channelsOut: ConcurrentHashMap<Pair<String, String>, Channel<SignalMessage>> = ConcurrentHashMap()

            fun initIn(key: Pair<String, String>): Channel<SignalMessage> {
                channelsIn[key] = Channel()
                return channelsIn[key]!!
            }

            fun initOut(key: Pair<String, String>): Channel<SignalMessage> {
                channelsOut[key] = Channel()
                return channelsOut[key]!!
            }

            suspend fun send(key: Pair<String, String>, signalMessage: SignalMessage) {
                channelsIn[key]!!.send(signalMessage)
            }

            suspend fun receive(key: Pair<String, String>): SignalMessage? = channelsOut[key]?.receive()
            fun getChannel(key: Pair<String, String>): Channel<SignalMessage>? = channelsOut[key]

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
                if(!lineStations.contains(transport.id))
                    lineStations[transport.id] = lineDetails[transport.line.name]!!.first { l -> l.transporters.any { it.id == transport.id } }.stations
                return lineStations[transport.id]!!
            }

        }
    }

}