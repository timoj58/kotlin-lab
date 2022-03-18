package com.tabiiki.kotlinlab.model

import com.tabiiki.kotlinlab.configuration.TransportConfig
import java.util.*

data class Transport(private val config: TransportConfig) {
    val id = UUID.randomUUID()
    val transportId = config.transportId
    val capacity = config.capacity
    val linePosition: Pair<String, String>? = null
    val physics = Physics(config)

    companion object class Physics(private val config: TransportConfig){
        var acceleration: Double? = 0.0
        var velocity: Double? = 0.0
        var speed: Double? = 0.0
        var position: Double? = 0.0
        val weight = config.weight
        val power = config.power
        val topSpeed = config.topSpeed
    }
}