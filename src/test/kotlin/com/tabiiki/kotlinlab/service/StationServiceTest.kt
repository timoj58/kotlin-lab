package com.tabiiki.kotlinlab.service


import com.tabiiki.kotlinlab.configuration.TransportConfig
import com.tabiiki.kotlinlab.model.Transport
import com.tabiiki.kotlinlab.repo.StationRepo
import com.tabiiki.kotlinlab.util.LineBuilder
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock

internal class StationServiceTest {

    private val stationRepo = mock(StationRepo::class.java)
/*
    @BeforeEach
    fun `init`() {
        `when`(stationRepo.get()).thenReturn(LineBuilder().stations)
    }

    @Test
    fun `train departing station B`() = runBlocking {
        val stationService = StationServiceImpl(100, stationRepo)

        val channel = Channel<Transport>()

        val job = async { stationService.monitor("B", channel) }
        val transport =
            Transport(config = TransportConfig(transportId = 1, capacity = 1), lineId = "1", timeStep = 1000)
        transport.linePosition = Pair("B", "A")
        val job2 = async { channel.send(transport) }

        delay(100)

        val res = stationService.getDepartures("B")

        assertThat(res.any { it.transportId == transport.id }).isEqualTo(true)
        assertThat(res.size).isEqualTo(1)

        job.cancelAndJoin()
        job2.cancelAndJoin()
    }

    @Test
    fun `train arriving station B`() = runBlocking {
        val stationService = StationServiceImpl(100, stationRepo)

        val channel = Channel<Transport>()

        val job = async { stationService.monitor("B", channel) }
        val transport =
            Transport(config = TransportConfig(transportId = 1, capacity = 1), lineId = "1", timeStep = 1000)
        transport.linePosition = Pair("A", "B")
        val depart = async {
            transport.depart(
                LineBuilder().stations[0], LineBuilder().stations[1], LineBuilder().stations[2]
            )
        }
        val job2 = async { channel.send(transport) }

        delay(100)

        val res = stationService.getArrivals("B")

        assertThat(res.any { it.transportId == transport.id }).isEqualTo(true)
        assertThat(res.size).isEqualTo(1)

        job.cancelAndJoin()
        job2.cancelAndJoin()
        depart.cancelAndJoin()
    }
  */
}