package com.tabiiki.kotlinlab.service

import com.tabiiki.kotlinlab.model.Commuter
import com.tabiiki.kotlinlab.monitor.CommuterMonitor
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

interface CommuterService {
    fun getCommuterChannel(): Channel<Commuter>
    suspend fun generate()
}

@Service
class CommuterServiceImpl(
    @Value("\${network.time-step}") val timeStep: Long,
    val routeService: RouteService,
): CommuterService {
    private val commuterChannel = Channel<Commuter>()
    private val trackingChannel = Channel<Commuter>()

    private val commuterMonitor = CommuterMonitor()

    override fun getCommuterChannel(): Channel<Commuter> = commuterChannel

    override suspend fun generate() = coroutineScope {

        launch { commuterMonitor.monitor(trackingChannel) }

        do {
            delay(timeStep)
            //release X amounts of new commuters.  TBC.  variable likely makes sense. (for now 1)
            val commuter =  Commuter(
                commute = routeService.generate(),
                channel = trackingChannel,
                timeStep = timeStep,
                routeFactory = routeService.routeFactory())

            launch { commuter.track() }
            launch { commuterChannel.send(commuter) }
        } while (true)
    }


}