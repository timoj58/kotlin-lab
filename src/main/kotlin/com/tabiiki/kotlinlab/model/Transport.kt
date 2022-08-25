package com.tabiiki.kotlinlab.model

import com.tabiiki.kotlinlab.configuration.TransportConfig
import com.tabiiki.kotlinlab.factory.SignalMessage
import com.tabiiki.kotlinlab.factory.SignalValue
import com.tabiiki.kotlinlab.repo.LineDirection
import com.tabiiki.kotlinlab.repo.LineInstructions
import com.tabiiki.kotlinlab.util.HaversineCalculator
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.sqrt

enum class Status {
    ACTIVE, DEPOT, PLATFORM;

    fun moving(): Boolean = listOf(ACTIVE).contains(this)
}

enum class Instruction {
    STATIONARY, EMERGENCY_STOP, SCHEDULED_STOP,/*, LIMIT_10, LIMIT_20, LIMIT_30,*/ THROTTLE_ON;

    fun isMoving(): Boolean =
        listOf(THROTTLE_ON/*, LIMIT_10, LIMIT_20, LIMIT_30*/).contains(this)
}

interface ITransport {
    suspend fun track(channel: SendChannel<Transport>)
    suspend fun release(instruction: LineInstructions)
    suspend fun signal(channel: Channel<SignalMessage>)
    fun section(): Pair<String, String>
    fun platformFromKey(): Pair<String, String>
    fun platformToKey(terminal: Boolean = false): Pair<String, String>?
    fun platformKey(): Pair<String, String>
    fun addSection(section: Pair<String, String>)
    fun addSwitchSection(section: Pair<String, String>)
    fun lineDirection(): LineDirection
    fun getJourneyTime(): Triple<Pair<String, String>, Int, Double>
    fun atPlatform(): Boolean
    fun isStationary(): Boolean
    fun getSectionStationCode(): String
    fun getCurrentInstruction(): Instruction
    fun getPosition(): Double
}

data class Transport(
    private val config: TransportConfig,
    val line: Line,
    val timeStep: Long
) : ITransport {

    var id: UUID = UUID.randomUUID()
    val transportId = config.transportId
    private val carriage = Carriage(config.capacity)
    private val physics = Physics(config)
    var status = Status.DEPOT
    private var instruction = Instruction.STATIONARY
    var actualSection: Pair<String, String>? = null
    private var journey: LineInstructions? = null
    private var journeyTime = Triple(Pair("", ""), AtomicInteger(0), 0.0)
    private var sectionData: Pair<Pair<String, String>?, Pair<String, String>?> = Pair(null, null)

    override fun getJourneyTime() = Triple(journeyTime.first, journeyTime.second.get(), journeyTime.third)
    override fun atPlatform() = status == Status.PLATFORM && physics.velocity == 0.0
    override fun isStationary() = physics.velocity == 0.0 || instruction == Instruction.STATIONARY
    override fun getSectionStationCode(): String = section().first.substringAfter(":").replace("|", ":")
    override fun getCurrentInstruction(): Instruction = this.instruction
    override fun getPosition(): Double = this.physics.displacement

    override fun platformKey(): Pair<String, String> =
        Pair("${line.name}:${this.lineDirection()}", section().first.substringBefore("|"))

    override fun section(): Pair<String, String> =
        sectionData.second ?: sectionData.first!!

    override fun platformFromKey(): Pair<String, String> {
        val line = line.name
        val dir = journey?.direction ?: this.lineDirection()
        val stationId = journey?.from?.id ?: section().first.substringAfter(":")

        return Pair("$line:$dir", "$line:$stationId")
    }

    override fun platformToKey(terminal: Boolean): Pair<String, String>? {
        val line = line.name
        var dir = journey?.direction ?: return null
        if (terminal) dir = LineDirection.TERMINAL

        return Pair("$line:$dir", "$line:${journey!!.to.id}")
    }

    override fun addSection(section: Pair<String, String>) {
        assert(section.first.contains(":")) { "section is wrong $section" }
        sectionData = Pair(section, null)
    }

    override fun addSwitchSection(section: Pair<String, String>) {
        actualSection = section()
        addSection(section)
    }

    override suspend fun track(channel: SendChannel<Transport>) {
        val previousStatus = AtomicReference(Status.DEPOT)

        do {
            if (previousStatus.get() == Status.ACTIVE) channel.send(this)
            previousStatus.set(status)
            delay(timeStep)
        } while (true)
    }

    override suspend fun release(instruction: LineInstructions): Unit = coroutineScope {
        launch { startJourney(instruction) }
        launch { motionLoop(Instruction.STATIONARY) }
    }

    override suspend fun signal(channel: Channel<SignalMessage>) {
        val timeRegistered = System.currentTimeMillis()
        var previousMsg: SignalMessage? = null
        do {
            val msg = channel.receive()
            if (msg.timesStamp >= timeRegistered) {
                if (previousMsg == null
                    || msg.signalValue != previousMsg.signalValue
                    && !(msg.id ?: UUID.randomUUID()).equals(id)
                ) {

                    when (msg.signalValue) {
                        SignalValue.GREEN -> Instruction.THROTTLE_ON
                        // SignalValue.AMBER -> Instruction.LIMIT_20 // TODO this is not implemented properly
                        SignalValue.RED -> Instruction.EMERGENCY_STOP
                    }.also { instruction = it }

                    previousMsg = msg
                }
            }
        } while (status.moving())
    }

    override fun lineDirection(): LineDirection {
        if (actualSection != null) return LineDirection.TERMINAL

        val firstStation = getSectionStationCode()
        val fromCount = line.stations.count { it == firstStation }
        val toCount = line.stations.count { it == section().second }
        val fromIdx: Int?
        val toIdx: Int?

        return if (fromCount == toCount && fromCount == 1) {
            fromIdx = line.stations.indexOf(firstStation)
            toIdx = line.stations.indexOf(section().second)

            if (fromIdx > toIdx) LineDirection.NEGATIVE else LineDirection.POSITIVE
        } else {
            if (fromCount > 1) {
                toIdx = line.stations.indexOf(section().second)
                fromIdx = getIndex(firstStation, toIdx)
                return if (fromIdx > toIdx) LineDirection.NEGATIVE else LineDirection.POSITIVE
            } else {
                fromIdx = line.stations.indexOf(firstStation)
                toIdx = getIndex(section().second, fromIdx)
                if (fromIdx > toIdx) LineDirection.NEGATIVE else LineDirection.POSITIVE
            }
        }
    }

    private fun getIndex(station: String, idx: Int) = listOf(
        line.stations.indexOf(station),
        line.stations.lastIndexOf(station)
    ).first {
        it + 1 == idx || it - 1 == idx
    }

    private suspend fun motionLoop(newInstruction: Instruction) = coroutineScope {
        instruction = newInstruction
        do {
            delay(timeStep)
            if (physics.velocity > 0.0) journeyTime.second.incrementAndGet()
            physics.calcTimeStep(instruction)
            if (instruction.isMoving() && physics.shouldApplyBrakes(instruction)) instruction =
                Instruction.SCHEDULED_STOP
        } while (physics.displacement <= physics.distance)

        launch { stopJourney() }
    }

    private fun startJourney(instruction: LineInstructions) {
        journey = instruction
        status = Status.ACTIVE

        journey!!.let {
            journeyTime =
                Triple(Pair("${line.name}:${it.from.id}", it.to.id), AtomicInteger(0), physics.init(it.from, it.to))
        }
    }

    private suspend fun stopJourney() = coroutineScope {
        physics.reset()
        actualSection = null
        journey!!.let {
            sectionData = Pair(
                Pair("${line.name}:${it.from.id}", it.to.id),
                Pair("${line.name}:${it.to.id}", it.next.id)
            )
        }
        status = Status.PLATFORM
    }

    companion object {
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

            fun init(from: Station, to: Station): Double {
                distance = haversineCalculator.distanceBetween(start = from.position, end = to.position)
                return distance
            }

            private fun calculateForce(instruction: Instruction, percentage: Double = 100.0): Double {
                return when (instruction) {
                    Instruction.THROTTLE_ON -> percentage * (power.toDouble() / 100.0)
                    //  Instruction.LIMIT_10 -> percentage * (power.toDouble() / 1500.0)
                    //  Instruction.LIMIT_20 -> percentage * (power.toDouble() / 1000.0)
                    //  Instruction.LIMIT_30 -> percentage * (power.toDouble() / 500.0)
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

            fun shouldApplyBrakes(instruction: Instruction): Boolean {
                val stoppingDistance = distance - displacement
                val brakingForce = -power.toDouble()
                val brakingVelocity = velocity + (brakingForce / weight)
                val iterationsToPlatform = stoppingDistance / velocity
                val iterationsToBrakeToPlatform = stoppingDistance / abs(brakingVelocity)

                return when (instruction) {
                    Instruction.THROTTLE_ON -> ceil(iterationsToPlatform) == floor(iterationsToBrakeToPlatform)
                    //  Instruction.LIMIT_10 -> floor(iterationsToPlatform) == floor(iterationsToBrakeToPlatform)
                    //  Instruction.LIMIT_20 -> floor(iterationsToPlatform) * 3 == floor(iterationsToBrakeToPlatform)
                    //  Instruction.LIMIT_30 -> floor(iterationsToPlatform) * 3 == floor(iterationsToBrakeToPlatform) - 1
                    else -> ceil(iterationsToPlatform) == floor(iterationsToBrakeToPlatform)
                }
            }
        }
    }
}