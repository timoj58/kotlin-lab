package com.tabiiki.kotlinlab.model

import com.tabiiki.kotlinlab.configuration.LineConfig
import com.tabiiki.kotlinlab.configuration.TransportConfig

data class Line(
    val config: LineConfig,
    val transportConfig: List<TransportConfig>
) {
    val id = config.id
    val name = config.name
    val stations = config.stations
    val carriers =
        generateSequence { transportConfig.map { Transport(it) }.first { it.transportId == config.transportId } }.take(
            config.transportCapacity
        ).toList()
}