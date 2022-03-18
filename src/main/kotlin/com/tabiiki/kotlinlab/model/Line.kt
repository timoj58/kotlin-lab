package com.tabiiki.kotlinlab.model

import com.tabiiki.kotlinlab.configuration.LineConfig

data class Carrier(val id: Int, val capacity: Int){
    val currentStation: String? = null
}

data class Line(val config: LineConfig, val transportConfig: List<Carrier>) {
    val id = config.id
    val name = config.name
    val stations = config.stations
    val carriers = generateSequence{ transportConfig.first { it.id == config.transportId } }.take(config.transportCapacity).toList()
}