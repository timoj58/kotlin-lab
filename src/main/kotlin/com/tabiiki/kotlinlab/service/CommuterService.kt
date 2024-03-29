package com.tabiiki.kotlinlab.service

import com.tabiiki.kotlinlab.model.Commuter
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Service
class CommuterService(
    @Value("\${network.time-step}") val timeStep: Long,
    private val routeService: RouteService
) {
    private val commuterChannel = Channel<Commuter>()
    fun getCommuterChannel(): Channel<Commuter> = commuterChannel

    suspend fun generate(): Unit = coroutineScope {
        launch { routeService.listen() }

        do {
            delay(timeStep)
            // release X amounts of new commuters.  TBC.  variable likely makes sense. (for now 1)
            // also what happens when they complete journey? need to track  them.  and perhaps cap no of commuters once performance discovered
            val commuter = Commuter(
                commute = routeService.generate(),
                timeStep = timeStep
            ) {
                launch { commuterChannel.send(it) }
            }

            launch { commuter.initJourney() }
        } while (true)
    }
}
