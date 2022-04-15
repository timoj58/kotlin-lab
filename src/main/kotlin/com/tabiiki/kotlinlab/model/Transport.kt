package com.tabiiki.kotlinlab.model

import com.tabiiki.kotlinlab.configuration.TransportConfig
import com.tabiiki.kotlinlab.factory.SignalValue
import com.tabiiki.kotlinlab.service.LineInstructions
import com.tabiiki.kotlinlab.util.HaversineCalculator
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.sqrt


enum class Status {
    ACTIVE, DEPOT, PLATFORM
}

enum class Instruction {
    STATIONARY, EMERGENCY_STOP, SCHEDULED_STOP, LIMIT_10, LIMIT_20, LIMIT_30, THROTTLE_ON
}

interface TransportInstructions {
    suspend fun track(channel: SendChannel<Transport>)
    suspend fun release(instruction: LineInstructions)
    suspend fun signal(channel: Channel<SignalValue>)
}

data class Transport(
    private val config: TransportConfig,
    val lineId: String,
    val timeStep: Long
) : TransportInstructions {

    var id = UUID.randomUUID()
    val transportId = config.transportId
    val capacity = config.capacity
    var section = Pair("", "") //current(from), next(to)
    var status = Status.DEPOT
    private var previousStatus = Status.DEPOT
    private var instruction = Instruction.STATIONARY
    var journey: LineInstructions? = null
    private var journeyTime = Pair(Pair("", ""), AtomicInteger(0))
    private val physics = Physics(config)

    companion object
    class Physics(config: TransportConfig) {
        private val haversineCalculator = HaversineCalculator()
        private val drag = 0.95

        var distance: Double = 0.0
        var velocity: Double = 0.0
        var displacement: Double = 0.0
        val weight = config.weight
        val topSpeed = config.topSpeed
        val power = config.power

        fun reset() {
            displacement = 0.0
            velocity = 0.0
        }

        fun init(from: Station, to: Station) {
            distance = haversineCalculator.distanceBetween(start = from.position, end = to.position)
        }

        private fun calculateForce(instruction: Instruction, percentage: Double = 100.0): Double {
            return when (instruction) {
                Instruction.THROTTLE_ON -> percentage * (power.toDouble() / 100.0)
                Instruction.SCHEDULED_STOP -> percentage * (power.toDouble() / 100.0) * -1
                Instruction.EMERGENCY_STOP -> power.toDouble() * -1
                else -> 0.0
            }
        }

        private fun calculateAcceleration(force: Double): Double = force / weight.toDouble()

        fun calcTimeStep(instruction: Instruction) {
            var force = calculateForce(instruction)
            var acceleration = calculateAcceleration(force)
            var percentage = 100.0

            while (velocity + acceleration > topSpeed && percentage >= 0.0) {
                percentage--
                force = calculateForce(instruction = instruction, percentage = percentage)
                acceleration = calculateAcceleration(force = force)
            }

            if (velocity + acceleration >= 0.0) velocity += acceleration else velocity = sqrt(velocity)
            if (floor(velocity) == 0.0 && instruction == Instruction.EMERGENCY_STOP) velocity = 0.0

            velocity *= drag
            displacement += velocity
        }


        fun shouldApplyBrakes(): Boolean {
            val stoppingDistance = distance - displacement
            val brakingForce = -power.toDouble()
            val brakingVelocity = velocity + (brakingForce / weight)
            val iterationsToPlatform = stoppingDistance / velocity
            val iterationsToBrakeToPlatform = stoppingDistance / abs(brakingVelocity)

            return ceil(iterationsToPlatform) + 2 == floor(iterationsToBrakeToPlatform) - 1
        }
    }

    fun getJourneyTime() = Pair(journeyTime.first, journeyTime.second.get())
    fun atPlatform() = status == Status.PLATFORM && physics.velocity == 0.0
    fun isStationary() = physics.velocity == 0.0 || instruction == Instruction.STATIONARY

    override suspend fun track(channel: SendChannel<Transport>) {
        do {
            if (previousStatus != Status.PLATFORM) channel.send(this)
            previousStatus = status
            delay(timeStep)
        } while (true)
    }

    override suspend fun release(instruction: LineInstructions) {
        startJourney(instruction)
        motionLoop(Instruction.STATIONARY)
    }

    override suspend fun signal(channel: Channel<SignalValue>) {
        do {
            when (channel.receive()) {
                SignalValue.GREEN -> Instruction.THROTTLE_ON
                SignalValue.AMBER_10 -> Instruction.LIMIT_10
                SignalValue.AMBER_20 -> Instruction.LIMIT_20
                SignalValue.AMBER_30 -> Instruction.LIMIT_30
                SignalValue.RED -> Instruction.EMERGENCY_STOP
            }.also { instruction = it }
        } while (status == Status.ACTIVE)
    }

    private suspend fun motionLoop(instruction: Instruction) {
        this.instruction = instruction
        do {
            delay(timeStep)
            journeyTime.second.incrementAndGet()
            physics.calcTimeStep(this.instruction)
            if (this.instruction == Instruction.THROTTLE_ON && physics.shouldApplyBrakes()) this.instruction =
                Instruction.SCHEDULED_STOP
        } while (physics.displacement <= physics.distance)
        stopJourney(Pair(journey!!.to.id, journey!!.next.id))
    }

    private fun startJourney(instruction: LineInstructions) {
        journey = instruction
        status = Status.ACTIVE
        journeyTime = Pair(Pair(journey!!.from.id, journey!!.to.id), AtomicInteger(0))
        physics.init(journey!!.from, journey!!.to)
    }

    private suspend fun stopJourney(newSection: Pair<String, String>) = coroutineScope {
        physics.reset()
        section = newSection
        status = Status.PLATFORM
    }
}