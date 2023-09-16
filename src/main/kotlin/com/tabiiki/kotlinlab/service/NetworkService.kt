package com.tabiiki.kotlinlab.service

import com.tabiiki.kotlinlab.factory.LineFactory
import com.tabiiki.kotlinlab.model.TransportMessage
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.springframework.stereotype.Service

@Service
class NetworkService(
    private val lineController: LineController,
    private val stationService: StationService,
    private val commuterService: CommuterService,
    private val lineFactory: LineFactory
) {
    private val lines = lineFactory.get().map { lineFactory.get(it) }

    suspend fun init() = coroutineScope {
        lineController.init(commuterService.getCommuterChannel())

        lines.groupBy { it.name }.values.forEach { line ->
            launch { lineController.init(line) }
        }
    }

    suspend fun start(
        stationReceiver: Channel<StationMessage>,
        transportReceiver: Channel<TransportMessage>
    ): Unit = coroutineScope {
        launch {
            stationService.start(
                globalListener = stationReceiver,
                commuterChannel = commuterService.getCommuterChannel()
            )
        }

        launch { lineFactory.tracking(channel = transportReceiver) }

        lines.groupBy { it.name }.values.forEach { line -> launch { lineController.start(line) } }
        launch { commuterService.generate() }
    }
}
