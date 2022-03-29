package com.tabiiki.kotlinlab.factory

import com.tabiiki.kotlinlab.configuration.LineConfig
import com.tabiiki.kotlinlab.configuration.LinesConfig
import com.tabiiki.kotlinlab.configuration.TransportConfig
import com.tabiiki.kotlinlab.configuration.TransportersConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito

internal class LineFactoryTest {
    val linesConfig = Mockito.mock(LinesConfig::class.java)
    val transportsConfig = Mockito.mock(TransportersConfig::class.java)

    @BeforeEach
    fun init() {

        Mockito.`when`(transportsConfig.get()).thenReturn(
            listOf(
                TransportConfig(1, 1000),
                TransportConfig(2, 1500),
            )
        )
    }


    @Test
    fun `get line`() {
        Mockito.`when`(linesConfig.lines).thenReturn(
            listOf(
                LineConfig("1", "1", 1, 10, 15, listOf("A", "C")),
                LineConfig("2", "2", 2, 10, 15, listOf("A", "B"))
            )
        )

        val factory = LineFactory(1, transportsConfig, linesConfig)
        val line = factory.get("1")

        assertNotNull(line)
        assertThat(line.stations).isEqualTo(listOf("A", "C"))
        assertThat(line.transporters.size).isEqualTo(10)
    }

    @Test
    fun `line does not exist`() {
        Mockito.`when`(linesConfig.lines).thenReturn(listOf())
        assertThrows(NoSuchElementException::class.java) {
            LineFactory(1, transportsConfig, linesConfig).get("1")
        }
    }
}