package com.tabiiki.kotlinlab.service

import com.tabiiki.kotlinlab.configuration.StationConfig
import com.tabiiki.kotlinlab.factory.StationFactory
import com.tabiiki.kotlinlab.model.Station
import com.tabiiki.kotlinlab.util.LineBuilder
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito

internal class StationsServiceImplTest {

    private val stationFactory = Mockito.mock(StationFactory::class.java)
    private val stations = LineBuilder().stations
    private val line = listOf("A", "B", "C")

    @BeforeEach
    fun `init`() {
        Mockito.`when`(stationFactory.get()).thenReturn(stations.map { it.id })
        Mockito.`when`(stationFactory.get("A")).thenReturn(stations.get(0))
        Mockito.`when`(stationFactory.get("B")).thenReturn(stations.get(1))
        Mockito.`when`(stationFactory.get("C")).thenReturn(stations.get(2))
    }


    @Test
    fun `next station is first in line`() {
        val stationsService = StationsServiceImpl(stationFactory)
        assertThat(stationsService.getNextStationOnLine(line, Pair("B", "A")).id).isEqualTo("B")
    }

    @Test
    fun `next station is last in line`() {
        val stationsService = StationsServiceImpl(stationFactory)
        assertThat(stationsService.getNextStationOnLine(line, Pair("B", "C")).id).isEqualTo("B")

    }

    @Test
    fun `next station is going forwards`() {
        val stationsService = StationsServiceImpl(stationFactory)
        assertThat(stationsService.getNextStationOnLine(line, Pair("A", "B")).id).isEqualTo("C")

    }

    @Test
    fun `next station is going reverse`() {
        val stationsService = StationsServiceImpl(stationFactory)
        assertThat(stationsService.getNextStationOnLine(line, Pair("C", "B")).id).isEqualTo("A")

    }

}