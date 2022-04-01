package com.tabiiki.kotlinlab.service

import com.tabiiki.kotlinlab.model.Status
import com.tabiiki.kotlinlab.model.Transport
import com.tabiiki.kotlinlab.repo.StationRepo
import com.tabiiki.kotlinlab.util.LineBuilder
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*

internal class PlatformConductorTest {


    private val stationRepo = mock(StationRepo::class.java)
    private val lineConductor = PlatformConductorImpl(stationRepo)

    private val transport = Transport(
        config = LineBuilder().transportConfig,
        lineId = "1",
        timeStep = 1
    ).also {
        it.status = Status.PLATFORM
        it.linePosition = Pair("A", "B")
    }

    @BeforeEach
    fun `init`() {
        `when`(stationRepo.get("A")).thenReturn(
            LineBuilder().stations[0]
        )
        `when`(stationRepo.get("B")).thenReturn(
            LineBuilder().stations[1]
        )
        `when`(
            stationRepo.getNextStationOnLine(
                listOf(), Pair("A", "B")
            )
        ).thenReturn(
            LineBuilder().stations[2]
        )
    }

    @Test
    fun `start test to ensure for multiple lines only one transport is scheduled to depart a given depot`() {

        val transporters = lineConductor.getFirstTransportersToDispatch(
            listOf(LineBuilder().getLine(), LineBuilder().getLine2())
        )

        assertThat(transporters.filter { it.linePosition == Pair("A", "B") }.size).isEqualTo(1)
        assertThat(transporters.filter { it.linePosition == Pair("C", "B") }.size).isEqualTo(1)
        assertThat(transporters.filter { it.linePosition == Pair("D", "C") }.size).isEqualTo(1)

        assertThat(transporters.size).isEqualTo(3)

    }


    @Test
    fun `train hold and dont release`() = runBlocking {
        val job = async {
            lineConductor.hold(
                transport = transport,
                lineStations = listOf()
            )
        }
        delay(5)

        verify(stationRepo, never()).getNextStationOnLine(listOf(), Pair("A", "B"))
        job.cancelAndJoin()

    }

    @Test
    fun `train hold and release`() = runBlocking {
        val job = async {
            lineConductor.hold(
                transport = transport,
                lineStations = listOf()
            )
        }

        delay(100)
        verify(stationRepo, atLeastOnce()).getNextStationOnLine(listOf(), Pair("A", "B"))

        job.cancelAndJoin()
    }

}