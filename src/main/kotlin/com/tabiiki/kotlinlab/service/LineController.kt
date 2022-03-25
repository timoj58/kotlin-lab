package com.tabiiki.kotlinlab.service

import com.tabiiki.kotlinlab.model.Line
import com.tabiiki.kotlinlab.model.Status
import com.tabiiki.kotlinlab.model.Transport
import com.tabiiki.kotlinlab.util.JourneyRepo
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel

interface LineController {
    suspend fun start(channel: Channel<Transport>)
    suspend fun regulate(channel: Channel<Transport>)
}

class LineControllerImpl(
    private val startDelay: Long,
    private val line: List<Line>,
    private val conductor: LineConductor,
    private val journeyRepo: JourneyRepo
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
                        if (journeyRepo.isLineSegmentClear(section, transport)
                            && journeyRepo.isJourneyTimeGreaterThanHoldingDelay(line, transport)
                        ) async { dispatch(transport, channel) }
                    }
            }

        } while (line.flatMap { it.transporters }.any { it.status == Status.DEPOT })

    }

    override suspend fun regulate(channel: Channel<Transport>) = coroutineScope {
        do {
            val message = channel.receive()
            if (message.isStationary()) {
                journeyRepo.addJourneyTime(message.getJourneyTime())
                async { conductor.hold(message, journeyRepo.getDefaultHoldDelay(line, message.id)) }
            }
        } while (true)
    }

    private suspend fun dispatch(transport: Transport, channel: Channel<Transport>) = coroutineScope {
        launch(Dispatchers.Default) { transport.track(channel) }
        launch(Dispatchers.Default) { conductor.depart(transport) }
    }

}