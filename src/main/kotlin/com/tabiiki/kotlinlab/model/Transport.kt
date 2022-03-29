package com.tabiiki.kotlinlab.model

import com.tabiiki.kotlinlab.configuration.TransportConfig
import com.tabiiki.kotlinlab.util.HaversineCalculator
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import java.util.*


enum class Status {
    ACTIVE, DEPOT
}

data class Transport(private val config: TransportConfig) {

    var id = UUID.randomUUID()
    val transportId = config.transportId
    val capacity = config.capacity
    var linePosition = Pair("", "") //current(from), next(to)
    private var previousLinePosition = Pair("", "")
    val physics = Physics(config)
    var holdCounter = 0
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

        //TODO probably dont need config as a param now
        fun reset(config: TransportConfig) {
            distance = 0.0
            acceleration = 0.0
            velocity = 0.0
            power = config.power

        }
    }

    fun getJourneyTime() = Pair(journeyTime, previousLinePosition)
    fun isStationary() = physics.acceleration == 0.0

    suspend fun track(channel: SendChannel<Transport>) {
        while (true) {
            channel.send(this)
            delay(1000)
        }
    }

    suspend fun depart(from: Station, to: Station, next: Station) {
        startJourney(from, to)
        do {
            delay(1000)
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

        async { hold() }
    }

    private suspend fun hold() {
        do {
            delay(1000)
            holdCounter++
        } while (isStationary())

        holdCounter = 0
    }


}