package com.tabiiki.kotlinlab.service

import com.tabiiki.kotlinlab.model.Line
import com.tabiiki.kotlinlab.model.Status
import com.tabiiki.kotlinlab.model.Transport
import com.tabiiki.kotlinlab.repo.JourneyRepo
import com.tabiiki.kotlinlab.repo.TransporterTrackerRepo
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.util.*

interface LineController {
    suspend fun start(channel: Channel<Transport>)
    suspend fun regulate(channel: Channel<Transport>, trackingRepoChannel: Channel<Transport>)
    fun getStationChannels(): Map<String, Channel<Transport>>
}

class LineControllerImpl(
    private val startDelay: Long,
    private val line: List<Line>,
    private val conductor: LineConductor,
    private val journeyRepo: JourneyRepo,
    private val stationChannels: Map<String, Channel<Transport>>,
    private val transporterTrackerRepo: TransporterTrackerRepo
) : LineController {

    override suspend fun start(channel: Channel<Transport>) = coroutineScope {
       
        conductor.getFirstTransportersToDispatch(line).forEach {
            async { dispatch(it, channel) }
        }

        do {
            delay(startDelay)

            conductor.getNextTransportersToDispatch(line).forEach { transport ->
                if (transporterTrackerRepo.isSectionClear(transport)) {
                    val difference = journeyRepo.isJourneyTimeGreaterThanHoldingDelay(line, transport)
                    if (difference > 0) async { delayThenDispatch(transport, channel, difference) }
                    else if (difference < 0) async { dispatch(transport, channel) }
                }
            }

        } while (line.flatMap { it.transporters }.any { it.status == Status.DEPOT })

    }

    override suspend fun regulate(channel: Channel<Transport>, trackingRepoChannel: Channel<Transport>) = coroutineScope {
        do {
            val message = channel.receive()
            async {  trackingRepoChannel.send(message) }

            listOf(message.linePosition.first, message.linePosition.second)
                .forEach { stationChannels[it]?.send(message) }

            if (message.atPlatform() && transporterTrackerRepo.isSectionClear(message)) {
                journeyRepo.addJourneyTime(message.getJourneyTime())
                async {
                    conductor.hold(
                        message,
                        journeyRepo.getDefaultHoldDelay(line, message.id),
                        getLineStations(message.id)
                    )
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
        launch(Dispatchers.Default) { conductor.depart(transport, getLineStations(transport.id)) }
    }

    private suspend fun delayThenDispatch(transport: Transport, channel: Channel<Transport>, delay: Int) =
        coroutineScope {
            var difference = delay
            val timeStep = line.first { l -> l.transporters.any { it.id == transport.id } }.timeStep
            do {
                delay(timeStep)
                difference--
            } while (difference > 0)
            async { dispatch(transport, channel) }
        }
}