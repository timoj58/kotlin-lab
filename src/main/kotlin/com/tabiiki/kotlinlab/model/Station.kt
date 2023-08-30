package com.tabiiki.kotlinlab.model

import com.tabiiki.kotlinlab.configuration.StationConfig

data class Station(
    private val config: StationConfig
) {
    val id = config.id
    val name = config.name
    val position = Pair(config.latitude, config.longitude)
}
