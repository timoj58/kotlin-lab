package com.tabiiki.kotlinlab.factory

import com.tabiiki.kotlinlab.configuration.LineConfig
import com.tabiiki.kotlinlab.configuration.NetworkConfig
import com.tabiiki.kotlinlab.model.Train
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito

internal class LineFactoryTest{
    val networkConfig = Mockito.mock(NetworkConfig::class.java)


    @BeforeEach
    fun init(){
        Mockito.`when`(networkConfig.lines).thenReturn(
            listOf(
                LineConfig("1", "1", 1, 10, listOf("A", "C")),
                LineConfig("2", "2", 2, 10, listOf("A", "B"))
            )
        )

        Mockito.`when`(networkConfig.trains).thenReturn(
            listOf(
                Train(1, 1000),
                Train(2, 1500),
            )
        )

    }


    @Test
    fun `get line`(){
       val line = LineFactory(networkConfig).get("1")

        assertNotNull(line)
        assertThat(line?.stations).isEqualTo(listOf("A", "C"))
        assertThat(line?.trains?.size).isEqualTo(10)
    }
}