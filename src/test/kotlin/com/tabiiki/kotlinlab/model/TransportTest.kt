package com.tabiiki.kotlinlab.model

import com.tabiiki.kotlinlab.configuration.StationConfig
import com.tabiiki.kotlinlab.configuration.TransportConfig
import com.tabiiki.kotlinlab.factory.SignalValue
import com.tabiiki.kotlinlab.service.LineDirection
import com.tabiiki.kotlinlab.service.LineInstructions
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class TransportTest {

    private val train = Transport(
        config = TransportConfig(transportId = 1, capacity = 10, power = 200, weight = 1000, topSpeed = 12),
        lineId = "1",
        timeStep = 10
    ).also { it.section = Pair("1", "2") }


    @Test
    fun `train moving between Stratford and West Ham stations`() = runBlocking {
        async { launch() }
        val channel = Channel<SignalValue>()
        val res = async { train.signal(channel) }
        delay(50)

        channel.send(SignalValue.GREEN)
        do {
            delay(1000)
        } while (!train.atPlatform())

        assertThat(train.section.first).isEqualTo("2")
        assertThat(train.section.second).isEqualTo("3")

        res.cancelAndJoin()
    }

    @Test
    fun `emergency stop test`() = runBlocking {
        async { launch() }
        val channel = Channel<SignalValue>()
        val res = async { train.signal(channel) }
        delay(50)

        channel.send(SignalValue.GREEN)
        delay(2000)

        channel.send(SignalValue.RED)
        delay(2000)

        assertThat(train.isStationary()).isEqualTo(true)
        channel.send(SignalValue.GREEN)

        do {
            delay(1000)
        } while (!train.atPlatform())

        assertThat(train.section.first).isEqualTo("2")
        assertThat(train.section.second).isEqualTo("3")

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