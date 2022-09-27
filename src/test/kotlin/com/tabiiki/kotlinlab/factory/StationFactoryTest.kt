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

    private val stationsConfig = Mockito.mock(StationsConfig::class.java)

    @Test
    fun `get station`() {
        Mockito.`when`(stationsConfig.stations).thenReturn(
            listOf(
                StationConfig(id = "A"),
                StationConfig(id = "B"),
                StationConfig(id = "C"),
            )
        )

        val station = StationFactory(stationsConfig).get("A")
        assertNotNull(station)
    }

    @Test
    fun `station does not exist`() {
        Mockito.`when`(stationsConfig.stations).thenReturn(listOf())
        Assertions.assertThrows(NoSuchElementException::class.java) {
            StationFactory(stationsConfig).get("A")
        }
    }
}