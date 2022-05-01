package com.tabiiki.kotlinlab.util

import com.tabiiki.kotlinlab.configuration.LineConfig
import com.tabiiki.kotlinlab.configuration.StationConfig
import com.tabiiki.kotlinlab.configuration.TransportConfig
import com.tabiiki.kotlinlab.model.Line
import com.tabiiki.kotlinlab.model.Station

class LineBuilder {
    val transportConfig =
        TransportConfig(transportId = 1, capacity = 100, weight = 1000, topSpeed = 75, power = 100)
    val stations = listOf(
        Station(
            StationConfig(id = "A", latitude = 51.541692575874, longitude = -0.00375164102719075),
            listOf()
        ),
        Station(StationConfig(id = "B", latitude = 51.528525530727, longitude = 0.00531739383278791), listOf()),
        Station(StationConfig(id = "C", latitude = 51.5002551610895, longitude = 0.00358625912595083), listOf())
    )


    fun getLine() = Line(
        timeStep = 10,
        config = LineConfig(
            id = "1",
            name = "1",
            transportId = 1,
            transportCapacity = 6,
            stations = listOf("A", "B", "C"),
            depots = listOf("A", "C")
        ), listOf(transportConfig)
    )

    fun getLine2() = Line(
        timeStep = 10,
        config = LineConfig(
            id = "2",
            name = "2",
            transportId = 1,
            transportCapacity = 6,
            stations = listOf("A", "B", "C", "D"),
            depots = listOf("A", "D")
        ), listOf(transportConfig)
    )

}