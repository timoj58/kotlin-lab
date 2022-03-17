package com.tabiiki.kotlinlab.configuration

import com.tabiiki.kotlinlab.configuration.adapter.LinesAdapter
import com.tabiiki.kotlinlab.enumerator.LineType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class LinesConfigTest{
    private val linesAdapter: LinesAdapter =
        LinesAdapter()

    @BeforeEach
    fun init(){

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
            mutableListOf("src/main/resources/network/overground/highbury.yml")
        )

    }

    @Test
    fun `load network`() {
        val lines = LinesConfig(linesAdapter);

        assertThat(lines.lines.size).isEqualTo(1)
        assertThat(lines.lines[0].type).isEqualTo(LineType.UNDERGROUND)
    }
}