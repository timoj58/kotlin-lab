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
import java.util.concurrent.ConcurrentHashMap
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
    suspend fun track(key: Pair<String, String>, channel: SendChannel<Transport>)
    fun removeTracker(key: Pair<String, String>)
    suspend fun release(instruction: LineInstructions)
    suspend fun signal(channel: Channel<SignalValue>)
    fun section(): Pair<String, String>
    fun platformFromKey(): Pair<String, String>
    fun platformToKey(): Pair<String, String>
    fun addSection(section: Pair<String, String>)
    fun previousSection(): Pair<String, String>
}

data class Transport(
    private val config: TransportConfig,
    val line: Line,
    val timeStep: Long
) : TransportInstructions {

    var id: UUID = UUID.randomUUID()
    val transportId = config.transportId
    private val capacity = config.capacity

    private val physics = Physics(config)

    var status = Status.DEPOT
    private var instruction = Instruction.STATIONARY

    private var journey: LineInstructions? = null
    private var journeyTime = Pair(Pair("", ""), AtomicInteger(0))
    private var sectionData: Pair<Pair<String, String>?, Pair<String, String>?> = Pair(null, null)
    private val trackers: ConcurrentHashMap<Pair<String, String>, SendChannel<Transport>> = ConcurrentHashMap()

    fun getJourneyTime() = Pair(journeyTime.first, journeyTime.second.get())
    fun atPlatform() = status == Status.PLATFORM && physics.velocity == 0.0
    fun isStationary() = physics.velocity == 0.0 || instruction == Instruction.STATIONARY

    override suspend fun track(key: Pair<String, String>, channel: SendChannel<Transport>) {
        var previousStatus = Status.DEPOT

        if (trackers.isEmpty()) {
            trackers[key] = channel
            do {
                if (previousStatus != Status.PLATFORM) trackers.values.forEach { it.send(this) }
                previousStatus = status
                delay(timeStep * 2)
            } while (true)
        } else trackers[key] = channel

    }

    override fun removeTracker(key: Pair<String, String>) {
        trackers.remove(key)
    }

    override suspend fun release(instruction: LineInstructions) {
        startJourney(instruction)
        motionLoop(Instruction.STATIONARY)
    }

    override suspend fun signal(channel: Channel<SignalValue>) {

        var previousMsg: SignalValue? = null
        do {
            val msg = channel.receive()
            if (previousMsg == null || msg != previousMsg) {
                when (msg) {
                    SignalValue.GREEN -> Instruction.THROTTLE_ON
                    SignalValue.AMBER_10 -> Instruction.LIMIT_10
                    SignalValue.AMBER_20 -> Instruction.LIMIT_20
                    SignalValue.AMBER_30 -> Instruction.LIMIT_30
                    SignalValue.RED -> Instruction.EMERGENCY_STOP
                }.also { instruction = it }
            }
            previousMsg = msg
        } while (status == Status.ACTIVE)
    }

    override fun section(): Pair<String, String> =
        sectionData.second ?: sectionData.first!!

    override fun platformFromKey(): Pair<String, String> {
        val line = line.name
        val dir = journey!!.direction

        return Pair("$line $dir", journey!!.from.id)
    }

    override fun platformToKey(): Pair<String, String> {
        val line = line.name
        val dir = journey!!.direction

        return Pair("$line $dir", journey!!.to.id)
    }

    override fun addSection(section: Pair<String, String>) {
        sectionData = Pair(section, null)
    }

    override fun previousSection(): Pair<String, String> = sectionData.first!!

    private suspend fun motionLoop(instruction: Instruction) {
        this.instruction = instruction
        do {
            delay(timeStep)
            journeyTime.second.incrementAndGet()
            physics.calcTimeStep(this.instruction)
            if (this.instruction == Instruction.THROTTLE_ON && physics.shouldApplyBrakes()) this.instruction =
                Instruction.SCHEDULED_STOP
        } while (physics.displacement <= physics.distance)

        stopJourney()
    }

    private fun startJourney(instruction: LineInstructions) {
        journey = instruction
        status = Status.ACTIVE

        journey?.let {
            physics.init(it.from, it.to)
            journeyTime = Pair(Pair(it.from.id, it.to.id), AtomicInteger(0))
        }
    }

    private suspend fun stopJourney() = coroutineScope {
        physics.reset()
        journey?.let {
            sectionData = Pair(
                Pair(it.from.id, it.to.id),
                Pair(it.to.id, it.next.id)
            )
        }
        status = Status.PLATFORM
    }

    companion object
    class Physics(config: TransportConfig) {
        private val haversineCalculator = HaversineCalculator()
        private val drag = 0.88

        var distance: Double = 0.0
        var velocity: Double = 0.0
        var displacement: Double = 0.0
        private val weight = config.weight
        private val topSpeed = config.topSpeed
        private val power = config.power

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
                Instruction.SCHEDULED_STOP -> percentage * (power.toDouble() / 1000.0) * -1
                Instruction.EMERGENCY_STOP -> power.toDouble() * -1
                else -> 0.0
            }
        }

        private fun calculateAcceleration(force: Double): Double = force / weight.toDouble()

        fun calcTimeStep(instruction: Instruction) {
            var force = calculateForce(instruction)
            var acceleration = calculateAcceleration(force)
            var percentage = 100.0

            // if(velocity + acceleration > topSpeed) println("above top speed"+(velocity + acceleration))

            while (velocity + acceleration > topSpeed && percentage >= 0.0) {
                percentage--
                force = calculateForce(instruction = instruction, percentage = percentage)
                acceleration = calculateAcceleration(force = force)
            }

            if (velocity + acceleration >= 0.0) velocity += acceleration else velocity = sqrt(velocity)
            if (floor(velocity) == 0.0 && instruction == Instruction.EMERGENCY_STOP) velocity = 0.0

            velocity *= drag
            displacement += velocity

            println("$instruction : $distance vs $displacement")
        }

        fun shouldApplyBrakes(): Boolean {
            val stoppingDistance = distance - displacement
            val brakingForce = -power.toDouble()
            val brakingVelocity = velocity + (brakingForce / weight)
            val iterationsToPlatform = stoppingDistance / velocity
            val iterationsToBrakeToPlatform = stoppingDistance / abs(brakingVelocity)

            //        println("$iterationsToPlatform $iterationsToBrakeToPlatform")
            return ceil(iterationsToPlatform) == floor(iterationsToBrakeToPlatform)
        }
    }
}