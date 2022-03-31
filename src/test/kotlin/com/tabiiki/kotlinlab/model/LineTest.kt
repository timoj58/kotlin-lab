package com.tabiiki.kotlinlab.model

import com.tabiiki.kotlinlab.configuration.LineConfig
import com.tabiiki.kotlinlab.util.LineBuilder
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import javax.naming.ConfigurationException

internal class LineTest {

    private val transportConfig = LineBuilder().transportConfig

    private val lineConfig = LineConfig(
        id = "1",
        name = "",
        transportId = 1,
        transportCapacity = 4,
        stations = listOf("A", "B", "C", "D"),
        depots = listOf("A", "D")
    )

    private val circleLineConfig = LineConfig(
        id = "1",
        name = "",
        transportId = 1,
        transportCapacity = 6,
        stations = listOf("A", "B", "C", "D", "A"),
        depots = listOf("A", "A")
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

        val line = Line(1, lineConfig, listOf(transportConfig))

        assertThat(line.transporters.count { t -> t.linePosition == Pair("A", "B") }).isEqualTo(2)
        assertThat(line.transporters.count { t -> t.linePosition == Pair("D", "C") }).isEqualTo(2)

    }

    @Test
    fun `invalid config test`() {
        Assertions.assertThrows(ConfigurationException::class.java) {
            Line(1, badLineConfig, listOf(transportConfig))
        }
    }

    @Test
    fun `circle line test where paddington is start and end`(){
        val line = Line(1, circleLineConfig, listOf(transportConfig))

        assertThat(line.transporters.count { t -> t.linePosition == Pair("A", "B") }).isEqualTo(3)
        assertThat(line.transporters.count { t -> t.linePosition == Pair("A", "D") }).isEqualTo(3)


    }

}