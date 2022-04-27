package com.tabiiki.kotlinlab.service

import com.tabiiki.kotlinlab.factory.SignalValue
import com.tabiiki.kotlinlab.model.Station
import com.tabiiki.kotlinlab.model.Transport
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap

enum class LineDirection {
    POSITIVE, NEGATIVE
}

data class LineInstructions(val from: Station, val to: Station, val next: Station, val direction: LineDirection)

interface LineSectionService {
    suspend fun release(transport: Transport, instructions: LineInstructions)
    suspend fun start(line: String)
}

@Service
class LineSectionServiceImpl(
    private val signalService: SignalService
) : LineSectionService {
    private val log = LoggerFactory.getLogger(this.javaClass)

    private val queues = Queues()
    private val channels = Channels()

    init {
        signalService.getSectionSignals().forEach { queues.initSectionQueues(it) }
        signalService.getPlatformSignals().forEach { queues.initPlatformQueues(it) }
    }

    override suspend fun release(
        transport: Transport,
        instructions: LineInstructions
    ) = coroutineScope {
        launch { transport.release(instructions) }

        val line = transport.line.name
        val dir = instructions.direction
        val key = Pair("$line $dir", transport.section().first)

        queues.platformRelease(key, transport)
    }

    override suspend fun start(line: String): Unit = coroutineScope {
        queues.getPlatformQueueKeys().forEach {
            launch { initPlatform(it) }
        }
        queues.getSectionQueueKeys().forEach {
            launch { initSection(it) }
        }
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

    private suspend fun addToLineSection(transport: Transport, channel: Channel<SignalValue>) = coroutineScope {
        queues.sectionRelease(transport.section(), transport)
        launch { transport.signal(channel) }
        launch { transport.track(transport.section(), queues.getSectionChannel(transport.section())) }
    }

    suspend fun monitorPlatformSignal(key: Pair<String, String>) = coroutineScope {
        do {
            channels.receive(key)?.let { queues.sendToPlatformQueue(key, it) }
        } while (true)
    }

    private suspend fun monitorPlatformChannel(key: Pair<String, String>) = coroutineScope {
        val channel = queues.getPlatformChannel(key)

        do {
            when (channel.receive()) {
                SignalValue.GREEN ->
                    queues.getPlatformQueue(key).firstOrNull()?.let {
                        channels.getChannel(it.section())?.let { channel ->
                            queues.getPlatformQueue(key).removeFirstOrNull()?.let { transport ->
                                log.info("<<<<< released (channel: $key): ${transport.id}")
                                launch { channels.send(key, SignalValue.RED) }
                                launch {
                                    delay(transport.timeStep * 2)
                                    addToLineSection(transport, channel)
                                }
                            }
                        }
                    }
                else -> {}
            }

        } while (true)
    }

    private suspend fun monitorSectionChannel(key: Pair<String, String>, channel: Channel<Transport>) = coroutineScope {
        do {
            val msg = channel.receive()
            when (msg.atPlatform()) {
                true ->
                    queues.getSectionQueue(key).removeLastOrNull()?.let {
                        it.removeTracker(key)
                        val platformFromKey = msg.platformFromKey()

                        log.info(">>>>> arrived (channel: $key): ${msg.id} ${msg.previousSection()} ${msg.getJourneyTime().second}")
                        launch { channels.send(platformFromKey, SignalValue.GREEN) }
                    }
                else -> {}
            }
        } while (true)
    }


    companion object {
        class Queues {
            private val platformQueues: ConcurrentHashMap<Pair<String, String>, Pair<Channel<SignalValue>, ArrayDeque<Transport>>> =
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
                if (platformQueues[key]!!.second.any { it.id == transport.id }) {
                    throw RuntimeException("duplicate release - FATAL $key ${transport.id}")
                }
                platformQueues[key]!!.second.addLast(transport)
            }

            fun sectionRelease(key: Pair<String, String>, transport: Transport) {
                sectionQueues[key]!!.second.addLast(transport)
            }

            suspend fun sendToPlatformQueue(key: Pair<String, String>, signalValue: SignalValue) {
                platformQueues[key]!!.first.send(signalValue)
            }

            fun getPlatformChannel(key: Pair<String, String>): Channel<SignalValue> = platformQueues[key]!!.first
            fun getSectionChannel(key: Pair<String, String>): Channel<Transport> = sectionQueues[key]!!.first
            fun getPlatformQueue(key: Pair<String, String>): ArrayDeque<Transport> = platformQueues[key]!!.second
            fun getSectionQueue(key: Pair<String, String>): ArrayDeque<Transport> = sectionQueues[key]!!.second

        }

        class Channels {
            private val channelsIn: ConcurrentHashMap<Pair<String, String>, Channel<SignalValue>> = ConcurrentHashMap()
            private val channelsOut: ConcurrentHashMap<Pair<String, String>, Channel<SignalValue>> = ConcurrentHashMap()

            fun initIn(key: Pair<String, String>): Channel<SignalValue> {
                channelsIn[key] = Channel()
                return channelsIn[key]!!
            }

            fun initOut(key: Pair<String, String>): Channel<SignalValue> {
                channelsOut[key] = Channel()
                return channelsOut[key]!!
            }

            suspend fun send(key: Pair<String, String>, signalValue: SignalValue) {
                channelsIn[key]!!.send(signalValue)
            }

            suspend fun receive(key: Pair<String, String>): SignalValue? = channelsOut[key]?.receive()
            fun getChannel(key: Pair<String, String>): Channel<SignalValue>? = channelsOut[key]

        }
    }

}