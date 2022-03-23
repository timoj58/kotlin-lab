package com.tabiiki.kotlinlab.model

import com.tabiiki.kotlinlab.configuration.TransportConfig
import com.tabiiki.kotlinlab.util.HaversineCalculator
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.delay
import org.slf4j.LoggerFactory
import java.util.*

data class Transport(private val config: TransportConfig) {

    private val logger = LoggerFactory.getLogger(this.javaClass.name)

    val id = UUID.randomUUID()
    val transportId = config.transportId
    val capacity = config.capacity
    var linePosition = Pair("", "") //current(from), next(to)
    val physics = Physics(config)
    private val haversineCalculator = HaversineCalculator()

    companion object
    class Physics(config: TransportConfig) {
        var acceleration: Double = 0.0
        var velocity: Double = 0.0
        var distance: Double = 0.0
        val weight = config.weight
        var power = config.power
        val topSpeed = config.topSpeed
    }

    suspend fun track(channel: SendChannel<Transport>) {
        while (true) {
            channel.send(this)
            delay(1000)
        }
    }

    suspend fun depart(from: Station, to: Station, next: Station) {
        logger.info("$id departing $from")

        physics.distance = haversineCalculator.distanceBetween(start = from.position, end = to.position)
        physics.acceleration = calcAcceleration()
        do {
            delay(1000)
            physics.distance = calcNewPosition()

        } while (physics.distance > 0.0)

        stop(next)
    }

    private fun topSpeedAsMetresPerSecond(): Double = physics.topSpeed / 3600.0
    private fun calcAcceleration(): Double = physics.power.div(physics.weight.toDouble())
    private fun calcVelocity(): Double {
        val velocity = physics.velocity.plus(physics.acceleration)
        return if (physics.acceleration >= 0.0 && velocity >= topSpeedAsMetresPerSecond()) physics.topSpeed.toDouble() else velocity
    }

    private fun brake(): Double {
        logger.info("$id braking for ${linePosition.second}")
        physics.acceleration = -calcAcceleration()
        physics.velocity = calcVelocity()
        return physics.distance - physics.velocity
    }


    private fun calcNewPosition(): Double {
        physics.velocity = calcVelocity()
        return if (physics.distance - physics.velocity * 4 < 0.0) brake() else physics.distance - physics.velocity
    }

    private fun stop(next: Station) {
        logger.info("$id arrived ${linePosition.second}")

        physics.distance = 0.0
        physics.acceleration = 0.0
        physics.velocity = 0.0
        physics.power = config.power
        linePosition = Pair(linePosition.second, next.id)

    }


}