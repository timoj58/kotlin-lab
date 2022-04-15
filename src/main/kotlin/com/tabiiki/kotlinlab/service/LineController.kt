package com.tabiiki.kotlinlab.service

import com.tabiiki.kotlinlab.model.Line
import com.tabiiki.kotlinlab.model.Status
import com.tabiiki.kotlinlab.model.Transport
import com.tabiiki.kotlinlab.repo.JourneyRepo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.*

interface LineController {
    suspend fun start(channel: Channel<Transport>)
    suspend fun regulate(channel: Channel<Transport>)
    fun getStationChannels(): Map<String, Channel<Transport>>
}

class LineControllerImpl(
    private val startDelay: Long,
    private val line: List<Line>,
    private val conductor: PlatformConductor,
    private val journeyRepo: JourneyRepo,
    private val stationChannels: Map<String, Channel<Transport>>
) : LineController {

    override suspend fun start(channel: Channel<Transport>) = coroutineScope {

        //NOTES:
        //1:  do not dispatch.  simply set the first trains to the platform, then set the platform signal...

        conductor.getFirstTransportersToDispatch(line).forEach {
            async { dispatch(it, channel) }
        }
        //2:  as platform has a train, wait for it to leave.....then add to platform light, trigger platform signal
        do {
            delay(startDelay)

            conductor.getNextTransportersToDispatch(line).forEach { transport ->
                async { dispatch(transport, channel) }  //TODO.  signal
            }

        } while (line.flatMap { it.transporters }.any { it.status == Status.DEPOT })

    }

    override suspend fun regulate(channel: Channel<Transport>) =
        coroutineScope {
            do {
                val message = channel.receive()
                async { publish(message) }
                if (message.atPlatform()) {
                    async { journeyRepo.addJourneyTime(message.getJourneyTime()) }
                    launch(Dispatchers.Default) {
                        conductor.hold(message, getLineStations(message.id))
                    }
                }
            } while (true)
        }

    override fun getStationChannels(): Map<String, Channel<Transport>> {
        return stationChannels
    }

    private fun getLineStations(id: UUID) =
        line.first { l -> l.transporters.any { it.id == id } }.stations


    private suspend fun dispatch(transport: Transport, channel: Channel<Transport>) = coroutineScope {
        launch(Dispatchers.Default) { transport.track(channel) }
        launch(Dispatchers.Default) { conductor.release(transport, getLineStations(transport.id)) }
    }

    private suspend fun publish(message: Transport) = coroutineScope {
        listOf(message.section.first, message.section.second)
            .forEach { stationChannels[it]?.send(message) }
    }
}