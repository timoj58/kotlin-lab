package com.tabiiki.kotlinlab.model

import com.tabiiki.kotlinlab.configuration.TransportConfig
import com.tabiiki.kotlinlab.util.HaversineCalculator
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import java.util.*


enum class Status {
    ACTIVE, DEPOT, PLATFORM
}

data class Transport(
    private val config: TransportConfig,
    val lineId: String,
    val timeStep: Long
) {

    var id = UUID.randomUUID()
    val transportId = config.transportId
    val capacity = config.capacity
    var linePosition = Pair("", "") //current(from), next(to)
    private var previousLinePosition = Pair("", "")
    private var previousStatus = Status.DEPOT
    val physics = Physics(config)
    var status = Status.DEPOT
    private var journeyTime = 0
    private val haversineCalculator = HaversineCalculator()

    companion object
    class Physics(config: TransportConfig) {
        var acceleration: Double = 0.0
        var velocity: Double = 0.0
        var distance: Double = 0.0
        val weight = config.weight
        var power = config.power
        val topSpeed = config.topSpeed

        fun reset(config: TransportConfig) {
            distance = 0.0
            acceleration = 0.0
            velocity = 0.0
            power = config.power

        }
    }

    fun getJourneyTime() = Pair(journeyTime, previousLinePosition)
    fun isStationary() = status == Status.ACTIVE && physics.acceleration == 0.0
    fun atPlatform() = status == Status.PLATFORM && physics.acceleration == 0.0
    suspend fun track(channel: SendChannel<Transport>) {
        while (true) {
            if(previousStatus != Status.PLATFORM) channel.send(this)
            previousStatus = status
            delay(timeStep)
        }
    }

    suspend fun depart(from: Station, to: Station, next: Station) {
        startJourney(from, to)
        do {
            delay(timeStep)
            journeyTime++
            physics.distance = calcNewPosition()

        } while (physics.distance > 0.0)

        stopJourney(next)
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

    private fun startJourney(from: Station, to: Station) {
        status = Status.ACTIVE
        journeyTime = 0

        physics.distance = haversineCalculator.distanceBetween(start = from.position, end = to.position)
        physics.acceleration = calcAcceleration()
    }

    private suspend fun stopJourney(next: Station) = coroutineScope {
        physics.reset(config)
        previousLinePosition = linePosition
        linePosition = Pair(linePosition.second, next.id)
        status = Status.PLATFORM

    }
}