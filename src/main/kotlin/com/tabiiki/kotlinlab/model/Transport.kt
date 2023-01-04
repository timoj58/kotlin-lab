package com.tabiiki.kotlinlab.model

import com.tabiiki.kotlinlab.configuration.TransportConfig
import com.tabiiki.kotlinlab.factory.SignalMessage
import com.tabiiki.kotlinlab.factory.SignalValue
import com.tabiiki.kotlinlab.monitor.SectionMessage
import com.tabiiki.kotlinlab.monitor.SwitchMonitor
import com.tabiiki.kotlinlab.repo.LineDirection
import com.tabiiki.kotlinlab.repo.LineInstructions
import com.tabiiki.kotlinlab.util.HaversineCalculator
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.sqrt

enum class Status {
    ACTIVE, DEPOT, PLATFORM;

    fun moving(): Boolean = listOf(ACTIVE).contains(this)
}

enum class Instruction {
    STATIONARY, EMERGENCY_STOP, SCHEDULED_STOP, THROTTLE_ON;

    fun isMoving(): Boolean = THROTTLE_ON == this
}

interface ITransport {
    suspend fun release(instruction: LineInstructions)
    suspend fun signal(channel: Channel<SignalMessage>)
    fun section(): Pair<String, String>
    fun platformFromKey(): Pair<String, String>
    fun platformToKey(terminal: Boolean = false): Pair<String, String>?
    fun platformKey(): Pair<String, String>
    fun addSection(section: Pair<String, String>? = null)
    fun addSwitchSection(section: Pair<String, String>)
    fun lineDirection(): LineDirection
    fun getJourneyTime(): Triple<Pair<String, String>, Int, Double>
    fun atPlatform(): Boolean
    fun isStationary(): Boolean
    fun getSectionStationCode(): String
    fun getCurrentInstruction(): Instruction
    fun getPosition(): Double
    fun setHoldChannel(holdChannel: Channel<Transport>)
    suspend fun arrived()
    fun switchSection(section: Pair<String, String>)
    fun setChannel(sectionChannel: Channel<SectionMessage>)
}

data class Transport(
    val id: UUID = UUID.randomUUID(),
    private val config: TransportConfig,
    val line: Line,
    val timeStep: Long
) : ITransport {

    val transportId = config.transportId
    val carriage = Carriage(config.capacity)
    private val physics = Physics(config)

    var status = Status.DEPOT
    var instruction = Instruction.STATIONARY
    private var actualSection: Pair<String, String>? = null
    private var journey: LineInstructions? = null
    private var journeyTime = Triple(Pair("", ""), AtomicInteger(0), 0.0)
    private var sectionData: Pair<Pair<String, String>?, Pair<String, String>?> = Pair(null, null)
    private var holdChannel: Channel<Transport>? = null
    private var sectionChannel: Channel<SectionMessage>? = null

    override fun getJourneyTime() = Triple(journeyTime.first, journeyTime.second.get(), journeyTime.third)
    override fun atPlatform() = status == Status.PLATFORM && physics.velocity == 0.0
    override fun isStationary() = physics.velocity == 0.0 || instruction == Instruction.STATIONARY
    override fun getSectionStationCode(): String = SwitchMonitor.replaceSwitch(Line.getStation(section().first))
    override fun getCurrentInstruction(): Instruction = this.instruction
    override fun getPosition(): Double = this.physics.displacement
    override fun setHoldChannel(holdChannel: Channel<Transport>) {
        this.holdChannel = holdChannel
    }

    override suspend fun arrived() {
        holdChannel!!.send(this)
        holdChannel = null
    }

    override fun switchSection(section: Pair<String, String>) {
        if (this.actualSection == null)
            this.actualSection = section
    }

    override fun setChannel(sectionChannel: Channel<SectionMessage>) {
        this.sectionChannel = sectionChannel
    }

    override fun platformKey(): Pair<String, String> =
        Pair("${line.name}:${this.lineDirection()}", section().first.substringBefore("|"))

    override fun section(): Pair<String, String> = sectionData.second ?: sectionData.first!!

    override fun platformFromKey(): Pair<String, String> {
        val line = line.name
        val dir = journey?.direction ?: this.lineDirection()
        val stationId = journey?.from?.id ?: Line.getStation(section().first)

        return Pair("$line:$dir", "$line:$stationId")
    }

    override fun platformToKey(terminal: Boolean): Pair<String, String>? {
        val line = line.name
        var dir = journey?.direction ?: return null
        if (terminal) dir = LineDirection.TERMINAL

        return Pair("$line:$dir", "$line:${journey!!.to.id}")
    }

    override fun addSection(section: Pair<String, String>?) {
        val key = section ?: actualSection!!
        assert(key.first.contains(":")) { "section is wrong $key" }
        sectionData = Pair(key, null)
    }

    override fun addSwitchSection(section: Pair<String, String>) {
        actualSection = section()
        addSection(section)
    }

    override suspend fun release(lineInstructions: LineInstructions): Unit = coroutineScope {
        startJourney(lineInstructions)
        launch { motionLoop() }
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

    private suspend fun motionLoop() = coroutineScope {
        do {
            delay(timeStep)

            if (physics.velocity > 0.0) journeyTime.second.incrementAndGet()
            physics.calcTimeStep(instruction)
            if (instruction.isMoving() && physics.shouldApplyBrakes(instruction)) instruction =
                Instruction.SCHEDULED_STOP

        } while (physics.displacement <= physics.distance)

         stopJourney()
    }

    private fun startJourney(lineInstructions: LineInstructions) {
        instruction = Instruction.STATIONARY
        journey = lineInstructions
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

        launch { sectionChannel!!.send(SectionMessage.ARRIVED) }
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
                    else -> ceil(iterationsToPlatform) == floor(iterationsToBrakeToPlatform)
                }
            }
        }
    }
}