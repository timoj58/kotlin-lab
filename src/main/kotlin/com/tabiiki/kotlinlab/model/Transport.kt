package com.tabiiki.kotlinlab.model

import com.tabiiki.kotlinlab.configuration.TransportConfig
import com.tabiiki.kotlinlab.factory.SignalValue
import com.tabiiki.kotlinlab.service.LineDirection
import com.tabiiki.kotlinlab.service.LineInstructions
import com.tabiiki.kotlinlab.util.HaversineCalculator
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.sqrt


enum class Status {
    ACTIVE, DEPOT, PLATFORM
}

enum class Instruction {
    STATIONARY, EMERGENCY_STOP, SCHEDULED_STOP, LIMIT_10, LIMIT_20, LIMIT_30, THROTTLE_ON;

    fun isMoving(): Boolean =
        listOf(THROTTLE_ON, LIMIT_10, LIMIT_20, LIMIT_30).contains(this)
}

interface TransportInstructions {
    suspend fun track(channel: SendChannel<Transport>)
    suspend fun release(instruction: LineInstructions)
    suspend fun signal(channel: Channel<SignalValue>)
    fun section(): Pair<String, String>
    fun platformFromKey(): Pair<String, String>
    fun platformToKey(): Pair<String, String>
    fun platformKey(): Pair<String, String>
    fun addSection(section: Pair<String, String>)
    fun previousSection(): Pair<String, String>
    fun lineDirection(): LineDirection
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
    val journal = Journal(id)

    var status = Status.DEPOT
    private var instruction = Instruction.STATIONARY

    private var journey: LineInstructions? = null
    private var journeyTime = Pair(Pair("", ""), AtomicInteger(0))
    private var sectionData: Pair<Pair<String, String>?, Pair<String, String>?> = Pair(null, null)
    private val trackers: ConcurrentHashMap<Pair<String, String>, SendChannel<Transport>> = ConcurrentHashMap()

    fun getJourneyTime() = Pair(journeyTime.first, journeyTime.second.get())
    fun atPlatform() = status == Status.PLATFORM && physics.velocity == 0.0
    fun isStationary() = physics.velocity == 0.0 || instruction == Instruction.STATIONARY

    override suspend fun track(channel: SendChannel<Transport>) {
        val previousStatus = AtomicReference(Status.DEPOT)

        do {
            if (previousStatus.get() != Status.PLATFORM) channel.send(this)
            previousStatus.set(status)
            delay(timeStep)
        } while (true)

    }

    override suspend fun release(instruction: LineInstructions): Unit = coroutineScope {
        launch { startJourney(instruction) }
        launch { motionLoop(Instruction.STATIONARY) }
    }

    override suspend fun signal(channel: Channel<SignalValue>) {

        var previousMsg: SignalValue? = null
        do {
            val msg = channel.receive()
            if (previousMsg == null || msg != previousMsg) {
                if (msg == SignalValue.GREEN) journal.add(
                    JournalRecord(action = JournalActions.DEPART, key = this.section())
                )
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

    override fun platformKey(): Pair<String, String> =
        Pair("${line.name} ${this.lineDirection()}", section().first)

    override fun lineDirection(): LineDirection {
        val fromCount = line.stations.count { it == section().first }
        val toCount = line.stations.count { it == section().second }
        val fromIdx: Int?
        val toIdx: Int?

        return if (fromCount == toCount && fromCount == 1) {
            fromIdx = line.stations.indexOf(section().first)
            toIdx = line.stations.indexOf(section().second)

            if (fromIdx > toIdx) LineDirection.NEGATIVE else LineDirection.POSITIVE
        } else {

            if (fromCount > 1) {
                toIdx = line.stations.indexOf(section().second)
                fromIdx = getIndex(section().first, toIdx)
                return if (fromIdx > toIdx) LineDirection.NEGATIVE else LineDirection.POSITIVE
            } else {
                fromIdx = line.stations.indexOf(section().first)
                toIdx = getIndex(section().second, fromIdx)
                if (fromIdx > toIdx) LineDirection.NEGATIVE else LineDirection.POSITIVE
            }
        }
    }

    override fun addSection(section: Pair<String, String>) {
        sectionData = Pair(section, null)
    }

    override fun previousSection(): Pair<String, String> = sectionData.first!!

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
            journeyTime.second.incrementAndGet()
            physics.calcTimeStep(instruction)
            if (instruction.isMoving() && physics.shouldApplyBrakes(instruction)) instruction =
                Instruction.SCHEDULED_STOP
        } while (physics.displacement <= physics.distance)

        launch { stopJourney() }
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
            journal.add(
                JournalRecord(
                    action = JournalActions.ARRIVE,
                    key = Pair(it.from.id, it.to.id)
                )
            )
        }
        status = Status.PLATFORM
    }

    companion object {
        private val log = LoggerFactory.getLogger(this.javaClass)

        enum class JournalActions { RELEASE, PLATFORM, DEPART, ARRIVE, ARRIVE_SECTION }
        data class JournalRecord(var id: UUID? = null, val action: JournalActions, val key: Pair<String, String>) {
            val milliseconds: Long = System.currentTimeMillis()
            fun print() = "$id: $action - $key"
        }

        class Journal(val id: UUID) {
            private val journal = mutableListOf<JournalRecord>()
            fun add(journalRecord: JournalRecord) {
                journal.add(journalRecord.also { it.id = this.id })
                //       log.info(journalRecord.print())
            }

            fun getLog() = journal
        }

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
                    Instruction.LIMIT_10 -> percentage * (power.toDouble() / 1500.0)
                    Instruction.LIMIT_20 -> percentage * (power.toDouble() / 1000.0)
                    Instruction.LIMIT_30 -> percentage * (power.toDouble() / 500.0)
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

                //   println("$instruction : $distance vs $displacement")
            }

            fun shouldApplyBrakes(instruction: Instruction): Boolean {
                val stoppingDistance = distance - displacement
                val brakingForce = -power.toDouble()
                val brakingVelocity = velocity + (brakingForce / weight)
                val iterationsToPlatform = stoppingDistance / velocity
                val iterationsToBrakeToPlatform = stoppingDistance / abs(brakingVelocity)

                //    println("$iterationsToPlatform $iterationsToBrakeToPlatform")

                return when (instruction) {
                    Instruction.THROTTLE_ON -> ceil(iterationsToPlatform) == floor(iterationsToBrakeToPlatform)
                    Instruction.LIMIT_10 -> floor(iterationsToPlatform) == floor(iterationsToBrakeToPlatform)
                    Instruction.LIMIT_20 -> floor(iterationsToPlatform) * 3 == floor(iterationsToBrakeToPlatform)
                    Instruction.LIMIT_30 -> floor(iterationsToPlatform) * 3 == floor(iterationsToBrakeToPlatform) - 1
                    else -> ceil(iterationsToPlatform) == floor(iterationsToBrakeToPlatform)
                }
            }
        }
    }
}