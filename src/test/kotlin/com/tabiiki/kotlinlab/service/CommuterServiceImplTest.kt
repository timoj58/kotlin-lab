package com.tabiiki.kotlinlab.service

import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.mockito.Mockito

class CommuterServiceImplTest {
    private val routeService = Mockito.mock(RouteService::class.java)
    private val commuterServiceImpl = CommuterServiceImpl(10, routeService)

    @Test
    fun `commuter service test check commuter added to channel` () = runBlocking {
        Mockito.`when` (routeService.generate()).thenReturn(Pair("A", "B"))

        val channel = commuterServiceImpl.getCommuterChannel()

        val job = launch { commuterServiceImpl.generate() }

        delay(100)

        val commuter = channel.receive()
        Assertions.assertThat(commuter).isNotNull
        Assertions.assertThat(commuter.commute.first).isNotNull
        Assertions.assertThat(commuter.commute.second).isNotNull

        job.cancel()
    }
}