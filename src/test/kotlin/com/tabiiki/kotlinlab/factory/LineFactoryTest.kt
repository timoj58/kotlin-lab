package com.tabiiki.kotlinlab.factory

import com.tabiiki.kotlinlab.configuration.LineConfig
import com.tabiiki.kotlinlab.configuration.LinesConfig
import com.tabiiki.kotlinlab.configuration.TrainsConfig
import com.tabiiki.kotlinlab.model.Train
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito

internal class LineFactoryTest{
    val linesConfig = Mockito.mock(LinesConfig::class.java)
    val trainsConfig = Mockito.mock(TrainsConfig::class.java)



    @BeforeEach
    fun init(){
        Mockito.`when`(linesConfig.lines).thenReturn(
            listOf(
                LineConfig("1", "1", 1, 10, listOf("A", "C")),
                LineConfig("2", "2", 2, 10, listOf("A", "B"))
            )
        )

        Mockito.`when`(trainsConfig.trains).thenReturn(
            listOf(
                Train(1, 1000),
                Train(2, 1500),
            )
        )

    }


    @Test
    fun `get line`(){
       val line = LineFactory(trainsConfig, linesConfig).get("1")

        assertNotNull(line)
        assertThat(line?.stations).isEqualTo(listOf("A", "C"))
        assertThat(line?.trains?.size).isEqualTo(10)
    }
}