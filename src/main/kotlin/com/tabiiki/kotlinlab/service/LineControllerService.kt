package com.tabiiki.kotlinlab.service

import com.tabiiki.kotlinlab.model.Line
import com.tabiiki.kotlinlab.model.Status
import com.tabiiki.kotlinlab.model.Transport
import com.tabiiki.kotlinlab.util.LineControllerUtilsImpl
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.util.*

interface LineController {
    suspend fun start(channel: Channel<Transport>)
    suspend fun regulate(channel: Channel<Transport>)
}

class LineControllerService(
    private val startDelay: Long,
    private val line: List<Line>,
    private val conductor: LineConductor,
    private val lineControllerUtils: LineControllerUtilsImpl
) : LineController {

    override suspend fun start(channel: Channel<Transport>) = coroutineScope {
        line.forEach { section ->
            section.transporters.groupBy { it.linePosition }.values.forEach {
                async {
                    dispatch(
                        it.first(),
                        channel
                    )
                }
            }
        }

        do {
            delay(startDelay)
            line.forEach { section ->
                section.transporters.filter { it.status == Status.DEPOT }
                    .groupBy { it.linePosition }.values.forEach {
                        val transport = it.first()
                        if (lineControllerUtils.isLineSegmentClear(section, transport)
                            && lineControllerUtils.isJourneyTimeGreaterThanHoldingDelay(line, transport)
                        ) async { dispatch(transport, channel) }
                    }
            }

        } while (line.flatMap { it.transporters }.any { it.status == Status.DEPOT })

    }

    override suspend fun regulate(channel: Channel<Transport>) = coroutineScope {
        do {
            val message = channel.receive()
            if (message.isStationary()) {
                val journeyTime = message.getJourneyTime()
                if (journeyTime.first != 0) lineControllerUtils.addJourneyTime(journeyTime.second, journeyTime.first)

                async { conductor.hold(message, lineControllerUtils.getDefaultHoldDelay(line, message.id)) }
            }
        } while (true)
    }

    private suspend fun dispatch(transport: Transport, channel: Channel<Transport>) = coroutineScope {
        launch(Dispatchers.Default) { transport.track(channel) }
        launch(Dispatchers.Default) { conductor.depart(transport) }
    }

}