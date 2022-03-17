package com.tabiiki.kotlinlab.factory

import com.tabiiki.kotlinlab.configuration.LineConfig
import com.tabiiki.kotlinlab.configuration.NetworkConfig
import com.tabiiki.kotlinlab.configuration.StationConfig
import com.tabiiki.kotlinlab.configuration.StationsConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito

internal class StationFactoryTest{

    val networkConfig = Mockito.mock(NetworkConfig::class.java)
    val stationsConfig = Mockito.mock(StationsConfig::class.java)


    @BeforeEach
    fun init(){
        Mockito.`when`(networkConfig.lines).thenReturn(
            listOf(
                LineConfig("1", "1", 1, 10, listOf("A", "C")),
                LineConfig("2", "2",2, 15, listOf("A", "B"))
            )
        )

        Mockito.`when`(stationsConfig.stations).thenReturn(
            listOf(
                StationConfig(id = "A"),
                StationConfig(id = "B"),
                StationConfig(id = "C"),
                )
        )

    }

    @Test
    fun `get station`(){

        val station = StationFactory(stationsConfig, networkConfig).get("A")
        assertNotNull(station)
        assertThat(station?.lines).isEqualTo(listOf("1", "2"))
    }

}