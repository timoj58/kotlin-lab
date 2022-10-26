package com.tabiiki.kotlinlab.model

import com.tabiiki.kotlinlab.service.RouteEnquiry
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test

class CarriageTest {

    private val carriage = Carriage(100)

    @Test
    fun `carriage test`() = runBlocking {

        val stationChannel = Channel<Commuter>()
        val routeEnquiryChannel = Channel<RouteEnquiry>()

        val commuter = Commuter(commute = Pair("B", "A"), stationChannel = Channel(), timeStep = 10, routeChannel = routeEnquiryChannel) {}

        val embarkJob = launch { carriage.embark(stationChannel) }
        delay(100)
        val channel = carriage.getChannel()
        launch {  channel.send(commuter) }
        delay(100)
        embarkJob.cancel()

        assert(!carriage.isEmpty())

        val job = launch { carriage.disembark("A", stationChannel) }

        delay(100)

        assert(carriage.isEmpty())
        job.cancel()
    }

}