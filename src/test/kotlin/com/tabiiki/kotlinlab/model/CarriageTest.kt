package com.tabiiki.kotlinlab.model

import com.tabiiki.kotlinlab.factory.RouteFactory
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock

class CarriageTest {

    private val carriage = Carriage(100)

    @Test
    fun `carriage test`() = runBlocking {

        TODO("this is broken")

        val stationChannel = Channel<Commuter>()
        val commuter = Commuter(commute = Pair("B", "A"), channel = Channel(), timeStep = 10, routeFactory = mock(RouteFactory::class.java))

        val embarkJob = launch { carriage.embark(stationChannel) }
        val channel = carriage.getChannel()
        channel.send(commuter)
        delay(100)
        embarkJob.cancel()

        assert(!carriage.isEmpty())

        val job = launch { carriage.disembark("A", stationChannel) }

        delay(100)

        assert(carriage.isEmpty())
        job.cancel()
    }

}