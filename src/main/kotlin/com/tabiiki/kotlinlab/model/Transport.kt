package com.tabiiki.kotlinlab.model

import com.tabiiki.kotlinlab.configuration.TransportConfig
import com.tabiiki.kotlinlab.util.HaversineCalculator
import kotlinx.coroutines.delay
import java.util.*

data class Transport(private val config: TransportConfig) {
    val id = UUID.randomUUID()
    val transportId = config.transportId
    val capacity = config.capacity
    var linePosition: Pair<String, String>? = null //current, next
    val physics = Physics(config)
    val haversineCalculator = HaversineCalculator()

    companion object
    class Physics(config: TransportConfig) {
        var acceleration: Double = 0.0
        var velocity: Double = 0.0
        var distance: Double = 0.0
        val weight = config.weight
        var power = config.power
        val topSpeed = config.topSpeed
    }

    suspend fun depart(from: Station, to: Station) {
        physics.distance = haversineCalculator.distanceBetween(start = from.position, end = to.position)
        physics.acceleration = calcAcceleration()
        do {
            delay(1000)
            physics.distance = calcNewPosition()

        } while (physics.distance > 0.0)

        stop()
    }

    private fun topSpeedAsMetresPerSecond(): Double = physics.topSpeed / 3600.0
    private fun calcAcceleration(): Double = physics.power.div(physics.weight.toDouble())
    private fun calcVelocity(): Double {
        val velocity = physics.velocity.plus(physics.acceleration)
        return if (physics.acceleration >= 0.0 && velocity >= topSpeedAsMetresPerSecond()) physics.topSpeed.toDouble() else velocity
    }

    private fun brake(): Double {
        physics.acceleration = -calcAcceleration()
        physics.velocity = calcVelocity()
        return physics.distance - physics.velocity
    }


    private fun calcNewPosition(): Double {
        physics.velocity = calcVelocity()
        return if (physics.distance - physics.velocity * 4 < 0.0) brake() else physics.distance - physics.velocity
    }

    private fun stop() {
        physics.distance = 0.0
        physics.acceleration = 0.0
        physics.velocity = 0.0
        physics.power = config.power
    }

}