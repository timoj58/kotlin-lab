package com.tabiiki.kotlinlab.service

import com.tabiiki.kotlinlab.configuration.TransportConfig
import com.tabiiki.kotlinlab.factory.LineFactory
import com.tabiiki.kotlinlab.model.Transport
import com.tabiiki.kotlinlab.util.LineBuilder
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

@Disabled // TODO these tests are most likely wrong.  ie switch lines occur on multiple lines, not a single line.
/*
    these do not make any sense:  review or leave.  testing data is wrong.
 */
internal class SwitchServiceImplTest {

    private val lineFactory = mock(LineFactory::class.java)
    private val switchService = SwitchService(lineFactory)

    @ParameterizedTest
    @CsvSource("B,C,TRUE", "C,B,TRUE", "C,D,FALSE")
    fun `is switch section 2`(to: String, from: String, res: Boolean) {
        `when`(lineFactory.isSwitchSection("3", Pair(to, from))).thenReturn(Pair(res, res))

        val transport = Transport(
            config = TransportConfig(transportId = 1, capacity = 10, power = 3800, weight = 1000, topSpeed = 28),
            line = LineBuilder().getSwitchLine2(),
            timeStep = 10
        ).also { it.addSection(Pair("3:$to", from)) }

        assertThat(switchService.isSwitchSection(transport)).isEqualTo(res)
    }

    @ParameterizedTest
    @CsvSource("B,C,FALSE", "C,B,FALSE", "C,D,TRUE")
    fun `is switch section`(to: String, from: String, res: Boolean) {
        `when`(lineFactory.isSwitchSection("3", Pair(to, from))).thenReturn(Pair(res, res))

        val transport = Transport(
            config = TransportConfig(transportId = 1, capacity = 10, power = 3800, weight = 1000, topSpeed = 28),
            line = LineBuilder().getSwitchLine1(),
            timeStep = 10
        ).also { it.addSection(Pair("3:$to", from)) }

        assertThat(switchService.isSwitchSection(transport)).isEqualTo(res)
    }

    @ParameterizedTest
    @CsvSource("B,C,FALSE", "C,B,FALSE", "D,C,TRUE")
    fun `is switch platform`(to: String, from: String, res: Boolean) {
        `when`(lineFactory.isSwitchSection("3", Pair(to, from))).thenReturn(Pair(res, res))

        val transport = Transport(
            config = TransportConfig(transportId = 1, capacity = 10, power = 3800, weight = 1000, topSpeed = 28),
            line = LineBuilder().getSwitchLine1(),
            timeStep = 10
        ).also { it.addSection(Pair("3:$to", from)) }

        assertThat(switchService.isSwitchPlatform(transport, transport.section())).isEqualTo(res)
    }
}
