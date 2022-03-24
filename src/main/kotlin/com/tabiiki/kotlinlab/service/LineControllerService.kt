package com.tabiiki.kotlinlab.service

import com.tabiiki.kotlinlab.model.Line
import com.tabiiki.kotlinlab.model.Status
import com.tabiiki.kotlinlab.model.Transport
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.*

interface Conductor {
    suspend fun hold(transport: Transport, delay: Int)
    suspend fun depart(transport: Transport)
}

@Service
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

interface LineController {
    suspend fun start(channel: Channel<Transport>)
    suspend fun regulate(channel: Channel<Transport>)
}

@Service
class LineControllerService(
    private val line: List<Line>,
    private val conductor: Conductor
) : LineController {

    private val journeyTimes = mutableMapOf<Pair<String, String>, Int>()

    override suspend fun start(channel: Channel<Transport>) = coroutineScope {
        line.forEach { section -> section.transporters.groupBy { it.linePosition }.values.forEach { async { dispatch(it.first(), channel) }} }

        do {
            delay(10000) //per 10 seconds is fine.
            line.forEach { section ->
                section.transporters.filter { it.status == Status.DEPOT }
                    .groupBy { it.linePosition }.values.forEach {
                        val transport = it.first()
                        if (isLineSegmentClear(section, transport)
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

    private fun isJourneyTimeGreaterThanHoldingDelay(transport: Transport) =
        if(!journeyTimes.containsKey(transport.linePosition)) false else journeyTimes[transport.linePosition]!! > getDefaultHoldDelay(transport.id)

    private fun isLineSegmentClear(section: Line, transport: Transport) =
        section.transporters.filter { it.id != transport.id }.all { it.linePosition != transport.linePosition }

    private fun getDefaultHoldDelay(id: UUID): Int =
        line.first { l -> l.transporters.any { it.id == id } }.holdDelay

}