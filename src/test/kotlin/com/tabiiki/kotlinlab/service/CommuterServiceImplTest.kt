package com.tabiiki.kotlinlab.service

import com.tabiiki.kotlinlab.factory.AvailableRoute
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.test.annotation.DirtiesContext

@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class CommuterServiceImplTest {
    private val routeService = Mockito.mock(RouteService::class.java)
    private val commuterServiceImpl = CommuterServiceImpl(10, routeService)

    @Test
    fun `commuter service test check commuter added to channel`() = runBlocking {
        val routeChannel = Channel<RouteEnquiry>()
        Mockito.`when`(routeService.generate()).thenReturn(Pair(Pair("A", "B"), routeChannel))

        val channelJob = launch {
            do {
                val enquiry = routeChannel.receive()
                enquiry.channel.send(
                    AvailableRoute(
                        route = mutableListOf(Pair("A", "B"))
                    )
                )
            } while (true)
        }

        val channel = commuterServiceImpl.getCommuterChannel()
        val job = launch { commuterServiceImpl.generate() }

        val commuter = channel.receive()

        Assertions.assertThat(commuter).isNotNull
        Assertions.assertThat(commuter.commute.first).isNotNull
        Assertions.assertThat(commuter.commute.second).isNotNull
        Assertions.assertThat(commuter.getCurrentStation()).isEqualTo("A")
        Assertions.assertThat(commuter.peekNextJourneyStage()).isEqualTo(Pair("A", "B"))

        job.cancel()
        channelJob.cancel()
    }
}
