package com.tabiiki.kotlinlab.util

import com.tabiiki.kotlinlab.configuration.LineConfig
import com.tabiiki.kotlinlab.configuration.TransportConfig
import com.tabiiki.kotlinlab.model.Line

class LineBuilder {
    val transportConfig =
        TransportConfig(transportId = 1, capacity = 100, weight = 1000, topSpeed = 75, power = 100)

    fun getLine(holdDelay: Int = 45) =  Line(
        LineConfig(
            id = "1",
            name = "2",
            transportId = 1,
            holdDelay = holdDelay,
            transportCapacity = 8,
            stations = listOf("A", "B", "C"),
            depots = listOf("A", "C")
        ), listOf(transportConfig)
    )
}