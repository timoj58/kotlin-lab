package com.tabiiki.kotlinlab.repo

import com.tabiiki.kotlinlab.factory.StationFactory
import com.tabiiki.kotlinlab.util.LineBuilder
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito

internal class StationRepoTest {

    private val stationFactory = Mockito.mock(StationFactory::class.java)
    private val stations = LineBuilder().stations
    private val line = listOf("A", "B", "C")
    private val circleLine = listOf("A", "B", "C", "A")

    @BeforeEach
    fun `init`() {
        Mockito.`when`(stationFactory.get()).thenReturn(stations.map { it.id })
        Mockito.`when`(stationFactory.get("A")).thenReturn(stations.get(0))
        Mockito.`when`(stationFactory.get("B")).thenReturn(stations.get(1))
        Mockito.`when`(stationFactory.get("C")).thenReturn(stations.get(2))
    }


    @Test
    fun `next station is first in line`() {
        val stationRepo = StationRepoImpl(stationFactory)
        assertThat(stationRepo.getNextStationOnLine(line, Pair("B", "A")).id).isEqualTo("B")
    }

    @Test
    fun `next station is last in line`() {
        val stationRepo = StationRepoImpl(stationFactory)
        assertThat(stationRepo.getNextStationOnLine(line, Pair("B", "C")).id).isEqualTo("B")

    }

    @Test
    fun `next station is going forwards`() {
        val stationRepo = StationRepoImpl(stationFactory)
        assertThat(stationRepo.getNextStationOnLine(line, Pair("A", "B")).id).isEqualTo("C")

    }

    @Test
    fun `next station is going reverse`() {
        val stationRepo = StationRepoImpl(stationFactory)
        assertThat(stationRepo.getNextStationOnLine(line, Pair("C", "B")).id).isEqualTo("A")

    }

    @Test
    fun `circle line test where start and end is same`() {
        val stationRepo = StationRepoImpl(stationFactory)
        assertThat(stationRepo.getNextStationOnLine(circleLine, Pair("C", "A")).id).isEqualTo("C")
        assertThat(stationRepo.getNextStationOnLine(circleLine, Pair("A", "C")).id).isEqualTo("B")

    }

    @Test
    fun `circle line test where start and end is same reversed`() {
        val stationRepo = StationRepoImpl(stationFactory)
        assertThat(stationRepo.getNextStationOnLine(circleLine, Pair("B", "A")).id).isEqualTo("B")
        assertThat(stationRepo.getNextStationOnLine(circleLine, Pair("A", "B")).id).isEqualTo("C")


    }

}