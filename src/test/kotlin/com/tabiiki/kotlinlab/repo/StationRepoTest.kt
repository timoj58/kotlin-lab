package com.tabiiki.kotlinlab.repo

import com.tabiiki.kotlinlab.configuration.StationConfig
import com.tabiiki.kotlinlab.factory.StationFactory
import com.tabiiki.kotlinlab.model.Station
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
    private val circleLine2 = listOf("E", "F", "A", "B", "C", "A")

    @BeforeEach
    fun `init`() {
        val allStations = mutableListOf<Station>()
        allStations.addAll(stations)
        allStations.add(
            Station(
                StationConfig(id = "E", latitude = 51.5002551610895, longitude = 0.00358625912595083)
            )
        )
        allStations.add(
            Station(
                StationConfig(id = "F", latitude = 51.5002551610895, longitude = 0.00358625912595083)
            )
        )
        allStations.add(
            Station(
                StationConfig(id = "D", latitude = 51.5002551610895, longitude = 0.00358625912595083)
            )
        )

        Mockito.`when`(stationFactory.get()).thenReturn(allStations.map { it.id })
        Mockito.`when`(stationFactory.get("A")).thenReturn(stations[0])
        Mockito.`when`(stationFactory.get("B")).thenReturn(stations[1])
        Mockito.`when`(stationFactory.get("C")).thenReturn(stations[2])
        Mockito.`when`(stationFactory.get("D")).thenReturn(allStations[5])
        Mockito.`when`(stationFactory.get("E")).thenReturn(allStations[3])
        Mockito.`when`(stationFactory.get("F")).thenReturn(allStations[4])
    }

    @Test
    fun `next & previous when station is first in line`() {
        val stationRepo = StationRepo(stationFactory)
        assertThat(stationRepo.getNextStationOnLine(line, Pair("B", "A")).id).isEqualTo("B")
        assertThat(stationRepo.getPreviousStationOnLine(line, Pair("B", "A")).id).isEqualTo("C")

        assertThat(
            stationRepo.getPreviousStationsOnLine(
                listOf(LineBuilder().getLine()),
                "B",
                LineDirection.POSITIVE
            )[0].id
        ).isEqualTo("A")
    }

    @Test
    fun `next & previous when station is last in line`() {
        val stationRepo = StationRepo(stationFactory)
        assertThat(stationRepo.getNextStationOnLine(line, Pair("B", "C")).id).isEqualTo("B")
        assertThat(stationRepo.getPreviousStationOnLine(line, Pair("B", "C")).id).isEqualTo("A")

        assertThat(
            stationRepo.getPreviousStationsOnLine(
                listOf(LineBuilder().getLine()),
                "C",
                LineDirection.POSITIVE
            )[0].id
        ).isEqualTo("B")
    }

    @Test
    fun `next & previous when station is going forwards`() {
        val stationRepo = StationRepo(stationFactory)
        assertThat(stationRepo.getNextStationOnLine(line, Pair("A", "B")).id).isEqualTo("C")
        assertThat(stationRepo.getPreviousStationOnLine(line, Pair("A", "B")).id).isEqualTo("B")

        assertThat(
            stationRepo.getPreviousStationsOnLine(
                listOf(LineBuilder().getLine()),
                "B",
                LineDirection.POSITIVE
            )[0].id
        ).isEqualTo("A")
    }

    @Test
    fun `next & previous when station is going reverse`() {
        val stationRepo = StationRepo(stationFactory)
        assertThat(stationRepo.getNextStationOnLine(line, Pair("C", "B")).id).isEqualTo("A")
        assertThat(stationRepo.getPreviousStationOnLine(line, Pair("C", "B")).id).isEqualTo("B")

        assertThat(
            stationRepo.getPreviousStationsOnLine(
                listOf(LineBuilder().getLine()),
                "B",
                LineDirection.NEGATIVE
            )[0].id
        ).isEqualTo("C")
    }

    @Test
    fun `next & previous circle line test where start and end is same`() {
        val stationRepo = StationRepo(stationFactory)
        assertThat(stationRepo.getNextStationOnLine(circleLine, Pair("C", "A")).id).isEqualTo("C")
        assertThat(stationRepo.getNextStationOnLine(circleLine, Pair("A", "C")).id).isEqualTo("B")
        assertThat(stationRepo.getPreviousStationOnLine(circleLine, Pair("C", "A")).id).isEqualTo("B")
        assertThat(stationRepo.getPreviousStationOnLine(circleLine, Pair("A", "C")).id).isEqualTo("C")
        assertThat(stationRepo.getPreviousStationOnLine(circleLine2, Pair("C", "A")).id).isEqualTo("B")
        assertThat(stationRepo.getPreviousStationOnLine(circleLine2, Pair("A", "C")).id).isEqualTo("C")

        assertThat(
            stationRepo.getPreviousStationsOnLine(
                listOf(LineBuilder().getCircleLine()),
                "A",
                LineDirection.POSITIVE
            ).map { it.id }
        ).isEqualTo(listOf("B", "D"))
    }

    @Test
    fun `next & previous circle line test where start and end is same reversed`() {
        val stationRepo = StationRepo(stationFactory)
        assertThat(stationRepo.getNextStationOnLine(circleLine, Pair("B", "A")).id).isEqualTo("B")
        assertThat(stationRepo.getNextStationOnLine(circleLine, Pair("A", "B")).id).isEqualTo("C")
        assertThat(stationRepo.getPreviousStationOnLine(circleLine, Pair("B", "A")).id).isEqualTo("C")
        assertThat(stationRepo.getPreviousStationOnLine(circleLine, Pair("A", "B")).id).isEqualTo("B")
        assertThat(stationRepo.getPreviousStationOnLine(circleLine2, Pair("B", "A")).id).isEqualTo("C")
        assertThat(stationRepo.getPreviousStationOnLine(circleLine2, Pair("A", "B")).id).isEqualTo("F")

        assertThat(
            stationRepo.getPreviousStationsOnLine(
                listOf(LineBuilder().getCircleLine()),
                "A",
                LineDirection.NEGATIVE
            ).map { it.id }
        ).isEqualTo(listOf("B", "D"))
    }
}
