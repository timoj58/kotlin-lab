package com.tabiiki.kotlinlab.service

import com.tabiiki.kotlinlab.factory.LineFactory
import com.tabiiki.kotlinlab.model.TransportMessage
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service
import java.util.concurrent.CompletableFuture

interface NetworkService {
    suspend fun init()
    suspend fun start(stationReceiver: Channel<StationMessage>, transportReceiver: Channel<TransportMessage>)
}

@Service
class NetworkServiceImpl(
    private val lineController: LineController,
    private val stationService: StationService,
    private val commuterService: CommuterService,
    private val lineFactory: LineFactory,
    private val messageService: MessageService
) : NetworkService {
    private val lines = lineFactory.get().map { lineFactory.get(it) }

    @EventListener(ApplicationReadyEvent::class)
    fun launch() {
        CompletableFuture.runAsync {
            runBlocking {
                launch { init() }
                delay(1000)
                launch {
                    start(
                        stationReceiver = messageService.stationReceiver,
                        transportReceiver = messageService.transportReceiver
                    )
                }

                launch { messageService.stationTracker() }
                launch { messageService.transportTracker() }
            }
        }
    }

    override suspend fun init() = coroutineScope {
        lineController.init(commuterService.getCommuterChannel())

        lines.groupBy { it.name }.values.forEach { line ->
            launch { lineController.init(line) }
        }
    }

    override suspend fun start(
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
