package com.tabiiki.kotlinlab.model

import com.tabiiki.kotlinlab.configuration.LineConfig
import com.tabiiki.kotlinlab.configuration.TransportConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class LineTest {

    private val lineConfig = LineConfig(
        id = "1",
        name = "",
        transportId = 1,
        transportCapacity = 12,
        stations = listOf("A", "B", "C")

    )
    private val transportConfig = TransportConfig(
        transportId = 1,
        capacity = 100
    )

    @Test
    fun `init line test`() {
        val line = Line(lineConfig, listOf(transportConfig))

        assertThat(line.transporters.count { t -> t.linePosition == Pair("A", "B") }).isEqualTo(7)
        assertThat(line.transporters.count { t -> t.linePosition == Pair("C", "B") }).isEqualTo(5)

    }
}