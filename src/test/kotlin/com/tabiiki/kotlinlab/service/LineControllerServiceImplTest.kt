package com.tabiiki.kotlinlab.service

import com.tabiiki.kotlinlab.configuration.LineConfig
import com.tabiiki.kotlinlab.configuration.StationConfig
import com.tabiiki.kotlinlab.configuration.TransportConfig
import com.tabiiki.kotlinlab.factory.StationFactory
import com.tabiiki.kotlinlab.model.Line
import com.tabiiki.kotlinlab.model.Station
import com.tabiiki.kotlinlab.model.Transport
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.Mockito.mock
import java.util.*

internal class LineControllerServiceImplTest {

    private val stationFactory = mock(StationFactory::class.java)

    private val stations = listOf(
        Station(
            StationConfig(id = "A", latitude = 51.541692575874, longitude = -0.00375164102719075),
            listOf()
        ),
        Station(StationConfig(id = "B", latitude = 51.528525530727, longitude = 0.00531739383278791), listOf()),
        Station(StationConfig(id = "C", latitude = 51.5002551610895, longitude = 0.00358625912595083), listOf())
    )

    private val line = Line(
        LineConfig(
            id = "1",
            name = "2",
            transportId = 1,
            transportCapacity = 4,
            stations = listOf("A", "B", "C"),
            depots = listOf("A", "C")
        ), listOf(TransportConfig(transportId = 1, capacity = 100, weight = 1000, topSpeed = 75, power = 100))
    )

    @Test
    fun `start line and expect two trains to arrive at station B`() = runBlocking {
        Mockito.`when`(stationFactory.get()).thenReturn(stations.map { it.id })
        Mockito.`when`(stationFactory.get("A")).thenReturn(stations.get(0))
        Mockito.`when`(stationFactory.get("B")).thenReturn(stations.get(1))
        Mockito.`when`(stationFactory.get("C")).thenReturn(stations.get(2))

        val stationsService = StationsServiceImpl(stationFactory)

        val lineControllerService = LineControllerServiceImpl(listOf(line), stationsService)

        val channel = Channel<Transport>()
        val res = async { lineControllerService.start(channel) }
        val testRes = async { testChannel(channel, res) }
    }

    suspend fun testChannel(channel: Channel<Transport>, job: Job) {
        var trains = mutableMapOf<UUID, Transport>()
        val timeout = 1000 * 60
        val startTime = System.currentTimeMillis()
        do {
            val msg = channel.receive()
            if (!trains.containsKey(msg.id)) trains[msg.id] = msg

        } while (trains.values.map { it.linePosition }.any { it.first != "B" } && startTime + timeout > System.currentTimeMillis())

        assertThat(trains.values.map { it.linePosition.second }.containsAll(listOf("A", "C"))).isEqualTo(true)
        job.cancelAndJoin()
    }

}