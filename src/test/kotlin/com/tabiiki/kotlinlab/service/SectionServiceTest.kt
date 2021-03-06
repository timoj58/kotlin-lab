package com.tabiiki.kotlinlab.service

import com.tabiiki.kotlinlab.configuration.TransportConfig
import com.tabiiki.kotlinlab.model.Transport
import com.tabiiki.kotlinlab.repo.JourneyRepo
import com.tabiiki.kotlinlab.util.LineBuilder
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock


class SectionServiceTest {

    private val signalService = mock(SignalService::class.java)
    private val journeyRepo = mock(JourneyRepo::class.java)
    private val switchService = mock(SwitchService::class.java)
    private val sectionService = SectionServiceImpl(45, switchService, signalService, journeyRepo)

    private val transport = Transport(
        config = TransportConfig(transportId = 1, capacity = 10, power = 3800, weight = 1000, topSpeed = 28),
        line = LineBuilder().getLine(),
        timeStep = 10
    ).also { it.addSection(Pair("1:A", "B")) }

    @Test
    fun `added twice exception test`() = runBlocking {

        `when`(signalService.getChannel(transport.section())).thenReturn(Channel())

        launch { sectionService.initQueues(transport.section()) }
        delay(100)
        val job = launch { sectionService.accept(transport, Channel()) }
        delay(100)
        try {
            sectionService.accept(transport, Channel())
        } catch (e: RuntimeException) {
            assertThat(true).isEqualTo(true)
        }

        job.cancelAndJoin()
    }

}