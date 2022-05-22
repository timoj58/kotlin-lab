package com.tabiiki.kotlinlab.model

import com.tabiiki.kotlinlab.configuration.StationConfig
import com.tabiiki.kotlinlab.configuration.TransportConfig
import com.tabiiki.kotlinlab.factory.SignalMessage
import com.tabiiki.kotlinlab.factory.SignalValue
import com.tabiiki.kotlinlab.util.LineBuilder
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

internal class TransportTest {

    private val train = Transport(
        config = TransportConfig(transportId = 1, capacity = 10, power = 3800, weight = 1000, topSpeed = 28),
        line = LineBuilder().getLine(),
        timeStep = 10
    ).also { it.addSection(Pair("1:1", "2")) }

    @ParameterizedTest
    @CsvSource("1:A,B,POSITIVE", "1:D,A,POSITIVE", "1:A,D,NEGATIVE", "1:B,A,NEGATIVE")
    fun `line direction test for circle line`(from: String, to: String, direction: LineDirection) {

        val train = Transport(
            config = TransportConfig(transportId = 1, capacity = 10, power = 3800, weight = 1000, topSpeed = 28),
            line = LineBuilder().getCircleLine(),
            timeStep = 10
        ).also { it.addSection(Pair(from, to)) }


        assertThat(train.lineDirection()).isEqualTo(direction)
    }

    @ParameterizedTest
    @CsvSource("1:B,A,POSITIVE", "1:A,B,NEGATIVE", "1:D,A,POSITIVE", "1:A,D,NEGATIVE", "1:C,A,NEGATIVE")
    fun `line direction test for circle line 2`(from: String, to: String, direction: LineDirection) {

        val train = Transport(
            config = TransportConfig(transportId = 1, capacity = 10, power = 3800, weight = 1000, topSpeed = 28),
            line = LineBuilder().getCircleLine2(),
            timeStep = 10
        ).also { it.addSection(Pair(from, to)) }


        assertThat(train.lineDirection()).isEqualTo(direction)
    }

    @Test
    fun `at platform test`() {
        train.status = Status.PLATFORM
        assertThat(train.atPlatform()).isEqualTo(true)
    }

    @Test
    fun `is stationary test`() {
        assertThat(train.isStationary()).isEqualTo(true)
    }

    @Test
    fun `not at platform test`() {
        assertThat(train.atPlatform()).isEqualTo(false)
    }

    @ParameterizedTest
    @CsvSource("GREEN", "AMBER_10", "AMBER_20", "AMBER_30")
    fun `train moving between Stratford and West Ham stations`(signal: SignalValue) = runBlocking {
        async { launch() }
        val channel = Channel<SignalMessage>()
        val res = async { train.signal(channel) }
        delay(50)

        channel.send(SignalMessage(signal))
        do {
            delay(1000)
        } while (!train.atPlatform())

        assertThat(train.section().first).isEqualTo("1:2")
        assertThat(train.section().second).isEqualTo("3")

        res.cancelAndJoin()
    }

    @Test
    fun `emergency stop test`() = runBlocking {
        async { launch() }
        val channel = Channel<SignalMessage>()
        val res = async { train.signal(channel) }
        delay(50)

        channel.send(SignalMessage(SignalValue.GREEN))
        delay(10)
        channel.send(SignalMessage(SignalValue.RED))

        do {
            delay(10)
        } while (!train.isStationary())
        assertThat(train.isStationary()).isEqualTo(true)

        channel.send(SignalMessage(SignalValue.GREEN))

        do {
            delay(1000)
        } while (!train.atPlatform())

        res.cancelAndJoin()
    }

    private fun launch() = runBlocking {
        val stratford = Pair(51.541692575874, -0.00375164102719075)
        val westHam = Pair(51.528525530727, 0.00531739383278791)
        val northGreenwich = Pair(51.628525530727, 0.00531739383278791)

        async {
            train.release(
                LineInstructions(
                    from = Station(
                        StationConfig(id = "1", latitude = stratford.first, longitude = stratford.second), listOf()
                    ),
                    to = Station(
                        StationConfig(id = "2", latitude = westHam.first, longitude = westHam.second), listOf()
                    ),
                    next = Station(
                        StationConfig(id = "3", latitude = northGreenwich.first, longitude = northGreenwich.second),
                        listOf()
                    ),
                    direction = LineDirection.POSITIVE
                )
            )
        }
    }


}