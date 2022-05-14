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
import javax.naming.ConfigurationException

interface LineController {
    suspend fun start(line: List<Line>, channel: Channel<Transport>)
    fun getStationChannels(): Map<String, Channel<Transport>>
    fun setStationChannels(stationChannels: Map<String, Channel<Transport>>)
    fun diagnostics()
}

@Service
class LineControllerImpl(
    @Value("\${network.start-delay}") private val startDelay: Long,
    private val conductor: LineConductor
) : LineController {

    init {
        if (startDelay < 1000) throw ConfigurationException("start delay is to small, minimum 1000 ms")
    }

    override suspend fun start(line: List<Line>, channel: Channel<Transport>) = coroutineScope {
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
                .filter { conductor.isClear(it) }
                .forEach { transport ->
                    delay(transport.timeStep)
                    launch { dispatch(transport, channel) }
                }

        } while (line.flatMap { it.transporters }.any { it.status == Status.DEPOT })

    }

    override fun getStationChannels(): Map<String, Channel<Transport>> {
        return stationChannels
    }

    override fun setStationChannels(channels: Map<String, Channel<Transport>>) {
        stationChannels = channels
    }

    override fun diagnostics() {
        conductor.diagnostics()
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

    companion object {
        private var stationChannels: Map<String, Channel<Transport>> = mutableMapOf()
    }
}