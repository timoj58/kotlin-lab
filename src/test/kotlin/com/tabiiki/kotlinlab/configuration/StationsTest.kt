package com.tabiiki.kotlinlab.configuration

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class StationsTest {

    @Test
    fun `load stations`() {

        val stations = StationsConfig("src/main/resources/network/stations.csv")
        assertThat(stations.stations.size).isEqualTo(679)
        assertThat(stations.stations[0].id).isEqualTo("1")
        assertThat(stations.stations[678].id).isEqualTo("679")
    }

}