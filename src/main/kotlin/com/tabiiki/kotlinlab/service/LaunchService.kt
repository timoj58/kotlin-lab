package com.tabiiki.kotlinlab.service

import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.annotation.Profile
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service
import java.util.concurrent.CompletableFuture

@Profile("!test")
@Service
class LaunchService(
    private val networkService: NetworkService,
    private val messageService: MessageService
) {

    @EventListener(ApplicationReadyEvent::class)
    fun launch() {
        CompletableFuture.runAsync {
            runBlocking {
                launch { networkService.init() }

                delay(1000)

                launch {
                    networkService.start(
                        stationReceiver = messageService.stationReceiver,
                        transportReceiver = messageService.transportReceiver
                    )
                }

                launch { messageService.stationTracker() }
                launch { messageService.transportTracker() }
            }
        }
    }
}
