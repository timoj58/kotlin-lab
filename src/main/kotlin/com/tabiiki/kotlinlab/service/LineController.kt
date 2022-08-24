package com.tabiiki.kotlinlab.service

import com.tabiiki.kotlinlab.model.Line
import com.tabiiki.kotlinlab.model.Status
import com.tabiiki.kotlinlab.model.Transport
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.naming.ConfigurationException

data class LineChannelMessage(
    val line: String,
    val section: Pair<String, String>,
    val transporters: List<UUID>
)

interface LineController {
    suspend fun init(line: List<Line>)
    suspend fun start(line: List<Line>)
}

@Service
class LineControllerImpl(
    @Value("\${network.start-delay}") private val startDelay: Long,
    private val conductor: LineConductor
) : LineController {

    init {
        if (startDelay < 1000) throw ConfigurationException("start delay is to small, minimum 1000 ms")
    }

    override suspend fun init(line: List<Line>): Unit = coroutineScope {
        launch { monitor() }
        launch { conductor.init(line.map { it.name }.distinct().first(), line) }
    }

    override suspend fun start(line: List<Line>) = coroutineScope {
        conductor.getFirstTransportersToDispatch(line).forEach {
            launch { release(it, channel) }
        }
        do {
            delay(startDelay)
            conductor.getNextTransportersToDispatch(line)
                .forEach { transport ->
                    if (conductor.isClear(transport)) {
                        launch { hold(transport, channel) }
                    }
                }

        } while (line.flatMap { it.transporters }.any { it.status == Status.DEPOT })
    }

    private suspend fun release(transport: Transport, channel: Channel<Transport>) = coroutineScope {
        launch { conductor.release(transport) }
        launch { track(transport, channel) }
    }

    private suspend fun hold(transport: Transport, channel: Channel<Transport>) = coroutineScope {
        launch { conductor.hold(transport) }
        launch { track(transport, channel) }
    }

    private suspend fun track(transport: Transport, channel: Channel<Transport>) = coroutineScope {
        launch { transport.track(channel) }
    }

    private suspend fun monitor() = coroutineScope {
        do {
            val message = channel.receive()
            tracker[message.id] = message.section()
        } while (true)
    }

    companion object {
        private val channel: Channel<Transport> = Channel()
        private val tracker: ConcurrentHashMap<UUID, Pair<String, String>> = ConcurrentHashMap() //TODO something with this
    }
}