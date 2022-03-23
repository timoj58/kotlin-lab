package com.tabiiki.kotlinlab.service

import com.tabiiki.kotlinlab.configuration.StationConfig
import com.tabiiki.kotlinlab.factory.StationFactory
import com.tabiiki.kotlinlab.model.Station
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito

internal class StationsServiceImplTest{

    private val stationFactory = Mockito.mock(StationFactory::class.java)

    private val stations = listOf(
        Station(
            StationConfig(id = "A", latitude = 51.541692575874, longitude = -0.00375164102719075),
            listOf()
        ),
        Station(StationConfig(id = "B", latitude = 51.528525530727, longitude = 0.00531739383278791), listOf()),
        Station(StationConfig(id = "C", latitude = 51.5002551610895, longitude = 0.00358625912595083), listOf())
    )

    @BeforeEach
    fun `init`(){
        Mockito.`when`(stationFactory.get()).thenReturn(stations.map { it.id })
        Mockito.`when`(stationFactory.get("A")).thenReturn(stations.get(0))
        Mockito.`when`(stationFactory.get("B")).thenReturn(stations.get(1))
        Mockito.`when`(stationFactory.get("C")).thenReturn(stations.get(2))
    }


    @Test
    fun `next station is first in line`(){
        val stationsService = StationsServiceImpl(stationFactory)
        assertThat(stationsService.getNextStation(Pair("B", "A")).id).isEqualTo("B")
    }

    @Test
    fun `next station is last in line`(){
        val stationsService = StationsServiceImpl(stationFactory)
        assertThat(stationsService.getNextStation(Pair("B", "C")).id).isEqualTo("B")

    }

    @Test
    fun `next station is going forwards`(){
        val stationsService = StationsServiceImpl(stationFactory)
        assertThat(stationsService.getNextStation(Pair("A", "B")).id).isEqualTo("C")

    }

    @Test
    fun `next station is going reverse`(){
        val stationsService = StationsServiceImpl(stationFactory)
        assertThat(stationsService.getNextStation(Pair("C", "B")).id).isEqualTo("A")

    }
}