package com.tabiiki.kotlinlab.model

import com.tabiiki.kotlinlab.configuration.StationConfig
import com.tabiiki.kotlinlab.configuration.TransportConfig
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class TransportTest {

    @Test
    fun `train moving between Stratford and West Ham stations`() = runBlocking {

        val stratford = Pair(51.541692575874, -0.00375164102719075)
        val westHam = Pair(51.528525530727, 0.00531739383278791)
        val northGreenwich = Pair(51.628525530727, 0.00531739383278791)

        val train = Transport(
            config = TransportConfig(transportId = 1, capacity = 10, power = 1, weight = 20, topSpeed = 75),
            lineId = "1",
            timeStep = 10
        )
        train.section = Pair("1", "2")


        val res = async {
            train.depart(
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
            )
        }

        do {
            delay(1000)
        } while (!train.atPlatform())

        assertThat(train.section.first).isEqualTo("2")
        assertThat(train.section.second).isEqualTo("3")

        res.cancelAndJoin()
    }


}