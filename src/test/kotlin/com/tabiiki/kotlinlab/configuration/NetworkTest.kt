package com.tabiiki.kotlinlab.configuration

import com.tabiiki.kotlinlab.configuration.adapter.LinesAdapter
import com.tabiiki.kotlinlab.configuration.adapter.TrainsAdapter
import com.tabiiki.kotlinlab.enumerator.LineType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class NetworkTest {

    private val trainsAdapter: TrainsAdapter =
        TrainsAdapter()

    private val lines: LinesAdapter =
        LinesAdapter()

    @BeforeEach
    fun init(){


        lines.setCable(
            mutableListOf("src/main/resources/network/cable/cable.yml")
        )
        lines.setRiver(
            mutableListOf("src/main/resources/network/river/river.yml")
        )
        lines.setUnderground(
            mutableListOf("src/main/resources/network/underground/city.yml")
        )
        lines.setOverground(
            mutableListOf("src/main/resources/network/overground/highbury.yml")
        )

        trainsAdapter.setTrains(
            listOf(TrainConfig(1, 10))
        )
    }

    @Test
    fun `load network`() {
         val network = NetworkConfig(trainsAdapter, lines);

        assertThat(network.lines.size).isEqualTo(1)
        assertThat(network.lines[0].type).isEqualTo(LineType.UNDERGROUND)
    }
}