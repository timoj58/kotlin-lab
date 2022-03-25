package com.tabiiki.kotlinlab.model

import com.tabiiki.kotlinlab.configuration.LineConfig
import com.tabiiki.kotlinlab.configuration.TransportConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import javax.naming.ConfigurationException

internal class LineTest {

    private val transportConfig = TransportConfig(
        transportId = 1,
        capacity = 100
    )

    private val lineConfig = LineConfig(
        id = "1",
        name = "",
        transportId = 1,
        transportCapacity = 4,
        stations = listOf("A", "B", "C", "D"),
        depots = listOf("A", "D")
    )

    private val badLineConfig = LineConfig(
        id = "1",
        name = "",
        transportId = 1,
        transportCapacity = 5,
        stations = listOf("A", "B", "C", "D"),
        depots = listOf("A", "D")
    )


    @Test
    fun `init line test with more than one train per station average`() {

        val line = Line(lineConfig, listOf(transportConfig))

        assertThat(line.transporters.count { t -> t.linePosition == Pair("A", "B") }).isEqualTo(2)
        assertThat(line.transporters.count { t -> t.linePosition == Pair("D", "C") }).isEqualTo(2)

    }

    @Test
    fun `invalid config test`(){
        Assertions.assertThrows(ConfigurationException::class.java) {
            Line(badLineConfig, listOf(transportConfig))
        }
    }

}