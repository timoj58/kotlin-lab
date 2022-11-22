package com.tabiiki.kotlinlab.configuration

import com.tabiiki.kotlinlab.configuration.adapter.LinesAdapter
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class LinesConfigTest {
    private val linesAdapter: LinesAdapter =
        LinesAdapter()

    @BeforeEach
    fun init() {

        linesAdapter.setCable(
            mutableListOf("src/main/resources/network/cable/cable.yml")
        )
        linesAdapter.setRiver(
            mutableListOf("src/main/resources/network/river/river.yml")
        )
        linesAdapter.setUnderground(
            mutableListOf("src/main/resources/network/underground/city.yml")
        )
        linesAdapter.setOverground(
            mutableListOf("src/main/resources/network/overground/highbury-islington.yml")
        )
        linesAdapter.setDockland(
            mutableListOf("src/main/resources/network/dockland/dlr.yml")
        )
        linesAdapter.setTram(
            mutableListOf("src/main/resources/network/tram/tram.yml")
        )

    }

    @Test
    fun `load network`() {
        val lines = LinesConfig(linesAdapter)

        assertThat(lines.lines.size).isEqualTo(15)
    }
}