package com.tabiiki.kotlinlab.service

import com.tabiiki.kotlinlab.repo.StationRepo
import com.tabiiki.kotlinlab.util.LineBuilder
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock

internal class LineConductorTest {


    private val stationRepo = mock(StationRepo::class.java)
    private val lineConductor = LineConductorImpl(mock(LineService::class.java))


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
                listOf(), Pair("1:A", "B")
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

        assertThat(transporters.filter { it.section() == Pair("1:A", "B") }.size).isEqualTo(1)
        assertThat(transporters.filter { it.section() == Pair("1:C", "B") }.size).isEqualTo(1)
        assertThat(transporters.filter { it.section() == Pair("2:D", "C") }.size).isEqualTo(1)
        assertThat(transporters.filter { it.section() == Pair("2:A", "B") }.size).isEqualTo(1)

        assertThat(transporters.size).isEqualTo(4) //no longer sharing lines.  to change this when we do

    }

}