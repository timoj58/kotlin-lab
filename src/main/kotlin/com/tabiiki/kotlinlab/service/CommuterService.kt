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
    private val routeService: RouteService,
) : CommuterService {
    private val commuterChannel = Channel<Commuter>()
    private val trackingChannel = Channel<Commuter>()

    private val commuterMonitor = CommuterMonitor()

    override fun getCommuterChannel(): Channel<Commuter> = commuterChannel

    override suspend fun generate() = coroutineScope {

        launch { routeService.listen() }
        launch { commuterMonitor.monitor(trackingChannel) }

        do {
            delay(timeStep)
            //release X amounts of new commuters.  TBC.  variable likely makes sense. (for now 1)
            //also what happens when they complete journey? need to track  them.
            val commuter = Commuter(
                commute = routeService.generate(),
                stationChannel = trackingChannel,
                timeStep = timeStep,
                routeChannel = routeService.getChannel(),
            ) {
                launch { it.track() }
                launch { commuterChannel.send(it) }
            }

            launch { commuter.initJourney() }

        } while (true)
    }


}