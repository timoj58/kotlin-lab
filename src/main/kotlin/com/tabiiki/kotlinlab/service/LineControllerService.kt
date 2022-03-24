package com.tabiiki.kotlinlab.service

import com.tabiiki.kotlinlab.model.Line
import com.tabiiki.kotlinlab.model.Status
import com.tabiiki.kotlinlab.model.Transport
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.util.*

interface Conductor {
    suspend fun hold(transport: Transport, delay: Int)
    suspend fun depart(transport: Transport)
}

class ConductorImpl(private val stationsService: StationsService) : Conductor {
    override suspend fun hold(transport: Transport, delay: Int): Unit = coroutineScope {
        if (transport.holdCounter > delay) launch(Dispatchers.Default) { depart(transport) }
    }

    override suspend fun depart(transport: Transport) {
        transport.depart(
            stationsService.get().first { it.id == transport.linePosition.first },
            stationsService.get().first { it.id == transport.linePosition.second },
            stationsService.getNextStation(transport.linePosition)
        )
    }
}

interface LineControllerService {
    suspend fun start(channel: Channel<Transport>)
    suspend fun regulate(channel: Channel<Transport>)
}

class LineControllerServiceImpl(
    private val line: List<Line>,
    private val conductor: Conductor
) : LineControllerService {

    private val journeyTimes = mutableMapOf<Pair<String, String>, Int>()

    override suspend fun start(channel: Channel<Transport>) = coroutineScope {
        line.forEach { section -> section.transporters.groupBy { it.linePosition }.values.forEach { async { dispatch(it.first(), channel) }} }

        do {
            delay(10000) //per 10 seconds is fine.
            line.forEach { section ->
                section.transporters.filter { it.status == Status.DEPOT }
                    .groupBy { it.linePosition }.values.forEach {
                        val transport = it.first()
                        if (isLineSegmentClear(section, transport.linePosition)
                            && isJourneyTimeGreaterThanHoldingDelay(transport)) async { dispatch(transport, channel) }
                    }
            }

        } while (line.flatMap { it.transporters }.any { it.status == Status.DEPOT })

    }

    override suspend fun regulate(channel: Channel<Transport>) = coroutineScope {
        do {
            val message = channel.receive()
            if (message.isStationary()) {
                val journeyTime = message.getJourneyTime()
                if (journeyTime.first != 0) journeyTimes[journeyTime.second] = journeyTime.first

                async { conductor.hold(message, getDefaultHoldDelay(message.id)) }
            }
        } while (true)
    }

    private suspend fun dispatch(transport: Transport, channel: Channel<Transport>) = coroutineScope {
        launch(Dispatchers.Default) { transport.track(channel) }
        launch(Dispatchers.Default) { conductor.depart(transport) }
    }

    private fun isJourneyTimeGreaterThanHoldingDelay(transport: Transport) = journeyTimes[transport.linePosition]!! > getDefaultHoldDelay(transport.id)
    private fun isLineSegmentClear(section: Line, linePosition: Pair<String, String>) = section.transporters.all { it.linePosition != linePosition }
    private fun getDefaultHoldDelay(id: UUID): Int = line.first { l -> l.transporters.any { it.id == id } }.holdDelay

}