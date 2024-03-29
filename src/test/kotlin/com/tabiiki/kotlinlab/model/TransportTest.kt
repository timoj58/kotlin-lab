package com.tabiiki.kotlinlab.model

import com.tabiiki.kotlinlab.configuration.StationConfig
import com.tabiiki.kotlinlab.configuration.TransportConfig
import com.tabiiki.kotlinlab.factory.SignalMessageV2
import com.tabiiki.kotlinlab.factory.SignalValue
import com.tabiiki.kotlinlab.service.LineDirection
import com.tabiiki.kotlinlab.service.LineInstructions
import com.tabiiki.kotlinlab.util.LineBuilder
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

@Disabled
internal class TransportTest {

    private val train = Transport(
        config = TransportConfig(transportId = 1, capacity = 10, power = 3800, weight = 1000, topSpeed = 28),
        line = LineBuilder().getLine(),
        timeStep = 10
    ).also {
        it.addSection(Pair("1:A", "B"))
    }

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

    @ParameterizedTest
    @CsvSource("1:A,B, POSITIVE", "1:B,A,NEGATIVE")
    fun `line direction test`(from: String, to: String, lineDirection: LineDirection) {
        train.addSection(Pair(from, to))
        assertThat(train.lineDirection()).isEqualTo(lineDirection)
    }

    @Test
    fun `test platform from and to with journey`() = runBlocking {
        train.startJourney(
            LineInstructions(
                from = LineBuilder().stations[0],
                to = LineBuilder().stations[1],
                next = LineBuilder().stations[2],
                direction = LineDirection.POSITIVE,
                minimumHold = 45
            )
        )
        val job = launch { train.motionLoop(Channel()) }

        val channel = Channel<SignalMessageV2>()

        val job2 = launch { train.monitorSectionSignal(channel) }
        delay(100)
        channel.send(SignalMessageV2(signalValue = SignalValue.GREEN, line = null))
        do {
            delay(100)
        } while (!train.atPlatform())

        assertThat(train.platformToKey()).isEqualTo(Pair("1:POSITIVE", "1:B"))

        job.cancel()
        job2.cancel()
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

    @ParameterizedTest
    @CsvSource("ACTIVE", "DEPOT")
    fun `not at platform test`(status: Status) {
        train.status = status
        assertThat(train.atPlatform()).isEqualTo(false)
    }

    @Test
    fun `add section exception test`() {
        Assertions.assertThrows(AssertionError::class.java) {
            train.addSection(Pair("A", "1:B"))
        }
    }

    @ParameterizedTest
    @CsvSource("GREEN"/*, "AMBER_10", "AMBER_20", "AMBER_30"*/)
    fun `train moving between Stratford and West Ham stations`(signal: SignalValue) = runBlocking {
        async { launch() }
        val channel = Channel<SignalMessageV2>()
        val res = async { train.monitorSectionSignal(channel) }
        delay(50)

        channel.send(SignalMessageV2(signal, line = null))
        do {
            delay(1000)
        } while (!train.atPlatform())

        assertThat(train.section().first).isEqualTo("1:2")
        assertThat(train.section().second).isEqualTo("3")

        res.cancel()
    }

    @Test
    fun `emergency stop test`() = runBlocking {
        async { launch() }
        val channel = Channel<SignalMessageV2>()
        val res = async { train.monitorSectionSignal(channel) }
        delay(50)

        channel.send(SignalMessageV2(SignalValue.GREEN, line = null))
        delay(500)
        channel.send(SignalMessageV2(SignalValue.RED, line = null))

        do {
            delay(10)
        } while (!train.isStationary())
        assertThat(train.isStationary()).isEqualTo(true)
        channel.send(SignalMessageV2(SignalValue.GREEN, line = null))

        do {
            delay(1000)
        } while (!train.atPlatform())

        res.cancel()
    }

    @Disabled
    @Test
    fun `issue a red signal during a scheduled stop `() = runBlocking {
        launch { launch() }
        val channel = Channel<SignalMessageV2>()
        val res = launch { train.monitorSectionSignal(channel) }

        delay(50)

        channel.send(SignalMessageV2(SignalValue.GREEN, line = null))

        do {
            delay(10)
        } while (train.instruction != Instruction.SCHEDULED_STOP)

        channel.send(SignalMessageV2(SignalValue.RED, line = null))

        delay(100)

        assertThat(train.atPlatform()).isEqualTo(false)

        channel.send(SignalMessageV2(SignalValue.GREEN, line = null))

        do {
            delay(100)
        } while (!train.atPlatform())

        res.cancel()
    }

    private fun launch() = runBlocking {
        val stratford = Pair(51.541692575874, -0.00375164102719075)
        val westHam = Pair(51.528525530727, 0.00531739383278791)
        val northGreenwich = Pair(51.628525530727, 0.00531739383278791)

        train.startJourney(
            LineInstructions(
                from = Station(
                    StationConfig(id = "1", latitude = stratford.first, longitude = stratford.second)
                ),
                to = Station(
                    StationConfig(id = "2", latitude = westHam.first, longitude = westHam.second)
                ),
                next = Station(
                    StationConfig(id = "3", latitude = northGreenwich.first, longitude = northGreenwich.second)
                ),
                direction = LineDirection.POSITIVE,
                minimumHold = 45
            )
        )

        launch {
            train.motionLoop(Channel())
        }
    }
}
