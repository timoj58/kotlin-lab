package com.tabiiki.kotlinlab.service

import com.tabiiki.kotlinlab.configuration.LineConfig
import com.tabiiki.kotlinlab.configuration.StationConfig
import com.tabiiki.kotlinlab.configuration.TransportConfig
import com.tabiiki.kotlinlab.model.Line
import com.tabiiki.kotlinlab.model.Station
import kotlinx.coroutines.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

internal class LineControllerServiceImplTest {

    private val lineControllerService = LineControllerServiceImpl(
        listOf(
            Line(
                LineConfig(
                    id = "1",
                    name = "2",
                    transportId = 1,
                    transportCapacity = 4,
                    stations = listOf("A", "B", "C"),
                    depots = listOf("A", "C")
                ), listOf(TransportConfig(transportId = 1, capacity = 100, weight = 1000, topSpeed = 75, power = 100))
            ),
        ),listOf(
            Station(
                StationConfig(id = "A", latitude = 51.541692575874, longitude = -0.00375164102719075),
                listOf()
            ),
            Station(StationConfig(id = "B", latitude = 51.528525530727, longitude = 0.00531739383278791), listOf()),
            Station(StationConfig(id = "C", latitude = 51.5002551610895, longitude = 0.00358625912595083), listOf())
        ))

        @Test
        fun `start line test`() = runBlocking<Unit> {
            val running = async {  lineControllerService.start() }

            delay(1000 * 10)
            assertThat(lineControllerService.areAnyTransportsRunning()).isEqualTo(true)
            delay(1000 * 40)
            assertThat( lineControllerService.areAnyTransportsRunning()).isEqualTo(false)
            running.cancelAndJoin()
        }

}