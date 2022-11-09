package com.tabiiki.kotlinlab.factory

import com.tabiiki.kotlinlab.service.RouteEnquiry
import com.tabiiki.kotlinlab.util.InterchangeFactoryBuilder
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

class RouteFactoryTest {
    private val routeFactory = RouteFactory(InterchangeFactoryBuilder().build())

    @Test
    fun `calculate route including circle line Bayswater to Stratford `() =
        runBlocking {
            val channel = Channel<AvailableRoute>()
            val job = launch {
                routeFactory.generateAvailableRoutes(RouteEnquiry(route = Pair("37", "528"), channel = channel, depth = 3))
            }

            val enquiries = testResults(
                channel,
                listOf(
                    listOf(
                        Pair("Circle:37", "Circle:418"),
                        Pair("Circle:418", "Circle:24"),
                        Pair("Jubilee:24", "Jubilee:528"),
                    )
                )
            )

            Assertions.assertThat(enquiries.isNotEmpty())
            job.cancel()
        }

    @Test
    fun `calculate routes from Stratford to Canary Wharf `() = runBlocking {
        val channel = Channel<AvailableRoute>()
        val job = launch {
            routeFactory.generateAvailableRoutes(RouteEnquiry(route = Pair("528", "94"), channel = channel))
        }

        val enquiries = testResults(
            channel,
            listOf(
                listOf(Pair("Jubilee:528", "Jubilee:94"),),
                listOf(Pair("DLR:528", "DLR:94"),)),
        )

        Assertions.assertThat(enquiries.isNotEmpty())
        job.cancel()
    }

    @Test
    fun `calculate routes for Beckton to Canary Wharf`() = runBlocking {
        val channel = Channel<AvailableRoute>()
        val job = launch {
            routeFactory.generateAvailableRoutes(RouteEnquiry(route = Pair("41", "94"), channel = channel))
        }

        //get results from channel.
        val enquiries = testResults(
            channel,
            listOf(
                listOf(
                    Pair("DLR:41", "DLR:95"),
                    Pair("Jubilee:95", "Jubilee:94"),
                )
            )
        )

        Assertions.assertThat(enquiries.isNotEmpty())
        job.cancel()
    }

    @Test
    fun `calculate route with virtual interchange from Emirates Royal Docks to Bermondsey `() = runBlocking {
        val channel = Channel<AvailableRoute>()
        val job = launch {
            routeFactory.generateAvailableRoutes(RouteEnquiry(route = Pair("655", "50"), channel = channel))
        }

        val enquiries = testResults(
            channel,
            listOf(
                listOf(Pair("Emirates Air Line:655", "Emirates Air Line:654"),Pair("Jubilee:396", "Jubilee:50")),
        ))

        Assertions.assertThat(enquiries.isNotEmpty())
        job.cancel()
    }

    private suspend fun testResults(
        channel: Channel<AvailableRoute>,
        test: List<List<Pair<String, String>>>
    ): List<List<Pair<String, String>>> {
        val enquiries :MutableList<List<Pair<String, String>>> = mutableListOf()
        do {
            val enquiry = channel.receive()
            println("$enquiry")
            enquiries.add(enquiry.route)
        } while (!enquiries.containsAll(test))


        return enquiries
    }

}