package com.tabiiki.kotlinlab.factory

import com.tabiiki.kotlinlab.configuration.LineConfig
import com.tabiiki.kotlinlab.configuration.LinesConfig
import com.tabiiki.kotlinlab.configuration.StationConfig
import com.tabiiki.kotlinlab.configuration.StationsConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito


internal class StationFactoryTest {

    val lineConfig = Mockito.mock(LinesConfig::class.java)
    val stationsConfig = Mockito.mock(StationsConfig::class.java)

    @BeforeEach
    fun init() {
        Mockito.`when`(lineConfig.lines).thenReturn(
            listOf(
                LineConfig("1", "1", 1, 10, 15, listOf("A", "C")),
                LineConfig("2", "2", 2, 15, 15, listOf("A", "B"))
            )
        )

    }

    @Test
    fun `get station`() {
        Mockito.`when`(stationsConfig.stations).thenReturn(
            listOf(
                StationConfig(id = "A"),
                StationConfig(id = "B"),
                StationConfig(id = "C"),
            )
        )

        val station = StationFactory(stationsConfig, lineConfig).get("A")
        assertNotNull(station)
        assertThat(station.lines).isEqualTo(listOf("1", "2"))
    }

    @Test
    fun `station does not exist`() {
        Mockito.`when`(stationsConfig.stations).thenReturn(listOf())
        Assertions.assertThrows(NoSuchElementException::class.java) {
            StationFactory(stationsConfig, lineConfig).get("A")
        }
    }
}