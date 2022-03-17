package com.tabiiki.kotlinlab.model

import com.tabiiki.kotlinlab.configuration.LineConfig

data class Train(val id: Int, val capacity: Int){
    val currentStation: String? = null
}

data class Line(val config: LineConfig, val trainConfig: List<Train>) {
    val id = config.id
    val name = config.name
    val stations = config.stations
    val trains = generateSequence{ trainConfig.first { it.id == config.train } }.take(config.totalTrains).toList()
}