package com.tabiiki.kotlinlab.integration

import com.tabiiki.kotlinlab.factory.StationFactory
import com.tabiiki.kotlinlab.model.Status
import com.tabiiki.kotlinlab.model.Transport
import com.tabiiki.kotlinlab.repo.JourneyRepoImpl
import com.tabiiki.kotlinlab.repo.StationRepoImpl
import com.tabiiki.kotlinlab.repo.TransporterTrackerRepoImpl
import com.tabiiki.kotlinlab.service.LineControllerImpl
import com.tabiiki.kotlinlab.service.PlatformConductorImpl
import com.tabiiki.kotlinlab.util.LineBuilder
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import java.util.*

class LineControllerTest {

    private val lineBuilder = LineBuilder()
    private val transporterTrackerRepo = TransporterTrackerRepoImpl()

    private val stationFactory = Mockito.mock(StationFactory::class.java)
    private val stations = lineBuilder.stations
    private val line = lineBuilder.getLine()

    @BeforeEach
    fun init() {
        Mockito.`when`(stationFactory.get()).thenReturn(stations.map { it.id })
        Mockito.`when`(stationFactory.get("A")).thenReturn(stations[0])
        Mockito.`when`(stationFactory.get("B")).thenReturn(stations[1])
        Mockito.`when`(stationFactory.get("C")).thenReturn(stations[2])
    }

    @Test
    fun `start line and expect two trains to arrive at station B`() = runBlocking {
        val stationRepo = StationRepoImpl(stationFactory)
        val lineControllerService =
            LineControllerImpl(
                1,
                listOf(line),
                PlatformConductorImpl(stationRepo),
                JourneyRepoImpl(),
                mapOf(),
                transporterTrackerRepo
            )

        val channel = Channel<Transport>()
        val channel2 = Channel<Transport>()
        val channel3 = Channel<Transport>()

        val res = async { lineControllerService.start(channel) }
        val res2 = async { lineControllerService.regulate(channel2, channel3) }
        val testRes = async { testChannel(channel, channel2, listOf(res, res2)) }
    }


    suspend fun testChannel(channel: Channel<Transport>, channel2: Channel<Transport>, jobs: List<Job>) {
        val trains = mutableMapOf<UUID, Transport>()
        line.transporters.forEach { trains[it.id] = it }
        val startTime = System.currentTimeMillis()
        do {
            val msg = channel.receive()
            channel2.send(msg)
            trains[msg.id] = msg

        } while (trains.values.map { it.status }
                .any { it == Status.DEPOT } && startTime + (1000 * 15) > System.currentTimeMillis())

        assertThat(trains.values.map { it.status }
            .any { it == Status.DEPOT }).isEqualTo(false)
        jobs.forEach { it.cancelAndJoin() }
    }

}