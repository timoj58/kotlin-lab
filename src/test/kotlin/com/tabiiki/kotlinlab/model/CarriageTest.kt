package com.tabiiki.kotlinlab.model

import com.tabiiki.kotlinlab.factory.AvailableRoute
import com.tabiiki.kotlinlab.service.RouteEnquiry
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.springframework.test.annotation.DirtiesContext

@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class CarriageTest {

    private val carriage = Carriage(100)

    @Test
    fun `carriage embark and disembark test`() = runBlocking {

        val stationChannel = Channel<Commuter>()
        val routeEnquiryChannel = Channel<RouteEnquiry>()

        val commuter = Commuter(
            commute = Pair("B", "A"),
            stationChannel = Channel(),
            timeStep = 10,
            routeChannel = routeEnquiryChannel
        ) {}

        val init = launch { commuter.initJourney() }

        launch { commuter.getChannel().send(
            AvailableRoute(route = mutableListOf(Pair("B", "A")))
        ) }

        delay(100)

        val embarkJob = launch { carriage.embark(stationChannel) }
        delay(100)
        val channel = carriage.getChannel()
        launch { channel.send(commuter) }
        delay(1000)
        embarkJob.cancel()

        assert(!carriage.isEmpty())

        val job = launch { carriage.disembark("A", stationChannel) }

        delay(1000)

        assert(carriage.isEmpty())
        job.cancel()
        init.cancel()
    }

}