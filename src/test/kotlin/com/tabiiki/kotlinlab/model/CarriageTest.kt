package com.tabiiki.kotlinlab.model

import com.tabiiki.kotlinlab.factory.AvailableRoute
import com.tabiiki.kotlinlab.service.RouteEnquiry
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test

class CarriageTest {

    private val carriage = Carriage(100)

    @Test
    fun `carriage embark and disembark test`() = runBlocking {
        val jobs = mutableListOf<Job>()
        val stationChannel = Channel<Commuter>()
        val routeEnquiryChannel = Channel<RouteEnquiry>()

        val commuter = Commuter(
            commute = Pair(Pair("B", "A"), routeEnquiryChannel),
            timeStep = 10
        ) {
            jobs.add(launch { stationChannel.send(it) })
        }

        jobs.add(launch { commuter.initJourney() })

        jobs.add(
            launch {
                commuter.channel.send(
                    AvailableRoute(route = mutableListOf(Pair("B", "A")))
                )
            }
        )

        delay(100)

        val embarkJob = launch { carriage.embark(stationChannel) }
        delay(100)
        val channel = carriage.channel
        jobs.add(launch { channel.send(commuter) })
        delay(100)
        embarkJob.cancel()

        assert(!carriage.isEmpty())

        jobs.add(launch { carriage.disembark("A", stationChannel) })

        delay(100)

        assert(carriage.isEmpty())
        jobs.forEach { it.cancel() }
    }
}
