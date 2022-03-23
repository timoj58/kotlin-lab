package com.tabiiki.kotlinlab.model

import com.tabiiki.kotlinlab.configuration.StationConfig
import com.tabiiki.kotlinlab.configuration.TransportConfig
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class TransportTest {

    @Test
    fun `train moving between Stratford and West Ham stations`() = runBlocking<Unit> {

        val stratford = Pair(51.541692575874, -0.00375164102719075)
        val westHam = Pair(51.528525530727, 0.00531739383278791)
        val northGreenwich = Pair(51.628525530727, 0.00531739383278791)

        val train = Transport(TransportConfig(transportId = 1, capacity = 10, power = 1, weight = 20, topSpeed = 75))

        train.depart(
            from = Station(
                StationConfig(id = "1", latitude = stratford.first, longitude = stratford.second), listOf()
            ),
            to = Station(
                StationConfig(id = "2", latitude = westHam.first, longitude = westHam.second), listOf()
            ),
            next = Station(
                StationConfig(id = "2", latitude = northGreenwich.first, longitude = northGreenwich.second), listOf()
            ),
        )

        assertThat(train.physics.distance).isEqualTo(0.0)
    }

}