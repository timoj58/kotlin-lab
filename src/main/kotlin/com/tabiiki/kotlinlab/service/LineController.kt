package com.tabiiki.kotlinlab.service

import com.tabiiki.kotlinlab.model.Line
import com.tabiiki.kotlinlab.model.Status
import com.tabiiki.kotlinlab.model.Transport
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

interface LineController {
    suspend fun start(channel: Channel<Transport>)
    fun getStationChannels(): Map<String, Channel<Transport>>
}

class LineControllerImpl(
    private val startDelay: Long,
    private val line: List<Line>,
    private val conductor: LineConductor,
    private val stationChannels: Map<String, Channel<Transport>>
) : LineController {

    override suspend fun start(channel: Channel<Transport>) = coroutineScope {
        launch { conductor.start(line.map { it.name }.distinct().first(), line) }

        conductor.getFirstTransportersToDispatch(line).forEach {
            delay(it.timeStep)
            launch {
                dispatch(it, channel)
            }
        }
        do {
            delay(startDelay)
            conductor.getNextTransportersToDispatch(line)
                .filter { conductor.clear(it) }
                .forEach { transport ->
                    delay(transport.timeStep)
                    launch { dispatch(transport, channel) }
                }

        } while (line.flatMap { it.transporters }.any { it.status == Status.DEPOT })

    }

    override fun getStationChannels(): Map<String, Channel<Transport>> {
        return stationChannels
    }

    private suspend fun dispatch(transport: Transport, channel: Channel<Transport>) = coroutineScope {
        launch { conductor.release(transport) }
        launch { publish(transport, channel) }

    }

    private suspend fun publish(transport: Transport, channel: Channel<Transport>) = coroutineScope {
        launch { transport.track(channel) }

        do {
            val message = channel.receive()
            listOf(message.section().first, message.section().second)
                .forEach { stationChannels[it]?.send(message) }

        } while (true)
    }
}