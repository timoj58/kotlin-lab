package com.tabiiki.kotlinlab.service

import com.tabiiki.kotlinlab.model.Line
import com.tabiiki.kotlinlab.model.Status
import com.tabiiki.kotlinlab.model.Transport
import com.tabiiki.kotlinlab.repo.JourneyRepo
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.util.*

interface LineController {
    suspend fun start(channel: Channel<Transport>)
    suspend fun regulate(channel: Channel<Transport>)
    fun getStationChannels(): Map<String, Channel<Transport>>
}

class LineControllerImpl(
    private val startDelay: Long,
    private val line: List<Line>,
    private val conductor: LineConductor,
    private val journeyRepo: JourneyRepo,
    private val stationChannels: Map<String, Channel<Transport>>
) : LineController {

    override suspend fun start(channel: Channel<Transport>) = coroutineScope {
        line.forEach { section ->
            section.transporters.groupBy { it.linePosition }.values.forEach {
                async { dispatch(it.first(), channel) }
            }
        }

        do {
            delay(startDelay)
            line.forEach { section ->
                section.transporters.filter { it.status == Status.DEPOT }
                    .groupBy { it.linePosition }.values.forEach {
                        val transport = it.first()
                        if (journeyRepo.isLineSegmentClear(section, transport)) {
                            val difference = journeyRepo.isJourneyTimeGreaterThanHoldingDelay(line, transport)
                            if (difference > 0) async { delayThenDispatch(transport, channel, difference) }
                            else if (difference < 0) async { dispatch(transport, channel) }
                        }
                    }
            }

        } while (line.flatMap { it.transporters }.any { it.status == Status.DEPOT })

    }

    override suspend fun regulate(channel: Channel<Transport>) = coroutineScope {
        do {
            val message = channel.receive()
            listOf(message.linePosition.first, message.linePosition.second)
                .forEach { stationChannels[it]?.send(message) }

            if (message.atPlatform()) {
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