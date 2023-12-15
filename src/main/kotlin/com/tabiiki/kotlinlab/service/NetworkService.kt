package com.tabiiki.kotlinlab.service

import com.tabiiki.kotlinlab.factory.LineFactory
import com.tabiiki.kotlinlab.model.TransportMessage
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.springframework.stereotype.Service

@Service
class NetworkService(
    private val lineController: LineController,
    private val stationService: StationService,
    private val commuterService: CommuterService,
    private val lineFactory: LineFactory,
    private val regulatorService: RegulatorService,
) {
    private val lines = lineFactory.get().map { lineFactory.get(it) }

    suspend fun init() = coroutineScope {
        regulatorService.init(lines = lines)
        launch { lineController.init(commuterChannel = commuterService.getCommuterChannel()) }
        delay(100)
        launch {
            lineController.subscribeStations(
                stationSubscribers = stationService.getSubscribers(
                    platformSignals = lineController.getPlatformSignals()
                )
            )
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

        launch { lineFactory.tracking(channels = listOf(transportReceiver, regulatorService.transportReceiver)) }
        lines.groupBy { it.name }.values.forEach { line -> launch { lineController.start(line) } }
        launch { commuterService.generate() }
        launch { regulatorService.regulate() }
    }
}
