package com.tabiiki.kotlinlab.service

import com.tabiiki.kotlinlab.factory.SignalValue
import com.tabiiki.kotlinlab.model.Station
import com.tabiiki.kotlinlab.model.Transport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
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
    private val platformQueues: ConcurrentHashMap<Pair<String, String>, Pair<Channel<SignalValue>, ArrayDeque<Transport>>> =
        ConcurrentHashMap()
    private val sectionQueues: ConcurrentHashMap<Pair<String, String>, Pair<Channel<Transport>, ArrayDeque<Transport>>> =
        ConcurrentHashMap()
    private val channelsIn: ConcurrentHashMap<Pair<String, String>, Channel<SignalValue>> = ConcurrentHashMap()
    private val channelsOut: ConcurrentHashMap<Pair<String, String>, Channel<SignalValue>> = ConcurrentHashMap()


    init {
        signalService.getSectionSignals().forEach { sectionQueues[it] = Pair(Channel(), ArrayDeque()) }
        signalService.getPlatformSignals().forEach { platformQueues[it] = Pair(Channel(), ArrayDeque()) }
    }

    override suspend fun release(
        transport: Transport,
        instructions: LineInstructions
    ) = coroutineScope {
        async { transport.release(instructions) }

        val line = transport.line.name
        val dir = instructions.direction
        val key = Pair("$line $dir", transport.section().first)
        platformQueues[key]!!.second.addLast(transport)
    }

    override suspend fun start(line: String) = coroutineScope {
        platformQueues.keys().asIterator().forEach {
            launch(Dispatchers.Default) { initPlatform(it) }
        }
        sectionQueues.keys().asIterator().forEach {
            launch(Dispatchers.Default) { initSection(it) }
        }
    }


    private suspend fun initPlatform(key: Pair<String, String>) = coroutineScope {
        launch(Dispatchers.Default) { initSignals(key) }
        launch(Dispatchers.Default) { monitorPlatformChannel(key) }
        launch(Dispatchers.Default) { monitorPlatformSignal(key) }
    }

    suspend fun monitorPlatformSignal(key: Pair<String, String>) = coroutineScope {
        do {
            channelsOut[key]?.receive()?.let { platformQueues[key]!!.first.send(it) }
        } while (true)
    }

    private suspend fun monitorPlatformChannel(key: Pair<String, String>) = coroutineScope {
        val channel = platformQueues[key]!!.first

        do {
            when (channel.receive()) {
                SignalValue.GREEN ->
                    platformQueues[key]!!.second.firstOrNull()?.let {
                        channelsOut[it.section()]?.let { channel ->
                            platformQueues[key]!!.second.removeFirstOrNull()?.let { transport ->
                                println("released $key ${transport.id}")
                                channelsIn[key]!!.send(SignalValue.RED)

                                async { transport.signal(channel) }
                                async { addToLineSection(transport) }
                            }
                        }
                    }
                else -> {}
            }

        } while (true)
    }

    private suspend fun addToLineSection(transport: Transport) = coroutineScope {
        sectionQueues[transport.section()]!!.second.addLast(transport)
        launch(Dispatchers.Default) { transport.signal(channelsOut[transport.section()]!!) }
        launch(Dispatchers.Default) { transport.track(transport.section(), sectionQueues[transport.section()]!!.first) }
    }

    private suspend fun initSection(key: Pair<String, String>) = coroutineScope {
        launch(Dispatchers.Default) { initSignals(key) }
        launch(Dispatchers.Default) { monitorSectionChannel(key, sectionQueues[key]!!.first) }
    }

    private suspend fun monitorSectionChannel(key: Pair<String, String>, channel: Channel<Transport>) = coroutineScope {
        do {
            val msg = channel.receive()
            when (msg.atPlatform()) {
                true -> sectionQueues[key]!!.second.removeLastOrNull()?.let {
                    it.removeTracker(key)

                    val platformFromKey = msg.platformFromKey()
                    val platformToKey = msg.platformToKey()

                    println("arrived ${msg.id} $platformToKey ${msg.previousSection()} ${msg.getJourneyTime().second}")
                    channelsIn[platformFromKey]!!.send(SignalValue.GREEN)
                }
                else -> {}
            }
        } while (true)
    }

    private suspend fun initSignals(key: Pair<String, String>) = coroutineScope {
        channelsIn[key] = Channel()
        channelsOut[key] = Channel()
        launch { signalService.start(key, channelsIn[key]!!, channelsOut[key]!!) }
    }

    //TODO.  improving.  next, need to add in section signals...next issue.

    /*
       currently.

       platform?  green or red.  once train arrives, its green.  but...

       can not have two trains at one platform, issue.  causes lock.

     */

}