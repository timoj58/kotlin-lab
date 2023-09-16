package com.tabiiki.kotlinlab.service

import com.tabiiki.kotlinlab.factory.SignalMessage
import com.tabiiki.kotlinlab.factory.SignalValue
import com.tabiiki.kotlinlab.model.Line
import com.tabiiki.kotlinlab.model.Transport
import com.tabiiki.kotlinlab.monitor.SectionMonitor
import com.tabiiki.kotlinlab.repo.JourneyRepo
import com.tabiiki.kotlinlab.repo.LineInstructions
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Consumer

private class Queues(private val minimumHold: Int, private val journeyRepo: JourneyRepo) {
    val queues: ConcurrentHashMap<Pair<String, String>, Pair<Channel<Transport>, ArrayDeque<Transport>>> =
        ConcurrentHashMap()

    fun getQueue(key: Pair<String, String>): ArrayDeque<Transport> = queues[key]!!.second

    fun initQueues(key: Pair<String, String>) {
        queues[key] = Pair(Channel(), ArrayDeque())
    }

    fun isClear(section: Pair<String, String>, incoming: Boolean, max: Int = 0): Pair<Boolean, Int> = Pair(
        queues[section]!!.second.isEmpty() ||
            (
                queues[section]!!.second.size < max &&
                    journeyRepo.getJourneyTime(section, minimumHold * 2 + 1).first > minimumHold * 2 &&
                    (if (incoming) incomingCheck(section) else defaultCheck(section))
                ),
        journeyTimeInSection(section)
    )

    private fun defaultCheck(section: Pair<String, String>) =
        checkDistanceTravelled(
            section,
            queues[section]!!.second.last().getPosition(),
            false
        ) && !queues[section]!!.second.last().isStationary()

    private fun incomingCheck(section: Pair<String, String>) =
        checkDistanceTravelled(
            section,
            queues[section]!!.second.first().getPosition(),
            true
        )

    private fun journeyTimeInSection(section: Pair<String, String>) =
        queues[section]!!.second.lastOrNull()?.getJourneyTime()?.second ?: 0

    private fun checkDistanceTravelled(
        section: Pair<String, String>,
        currentPosition: Double,
        incoming: Boolean
    ): Boolean {
        val journey = journeyRepo.getJourneyTime(section, 0)
        if (journey.second == 0.0 && !incoming) return true
        val predictedDistance = (journey.second / journey.first) * minimumHold * 2
        return if (!incoming) currentPosition > predictedDistance else currentPosition < predictedDistance
    }

    fun getQueueKeys(): List<Pair<String, String>> = queues.keys().toList()

    fun release(key: Pair<String, String>, transport: Transport) {
        val limit = transport.line.transportersPerSection
        if (queues[key]!!.second.size > limit) throw RuntimeException("${transport.id} Only $limit transporters ${queues[key]!!.second.map { it.id }} allowed in $key")
        queues[key]!!.second.addLast(transport)
    }

    fun getChannel(key: Pair<String, String>): Channel<Transport> = queues[key]!!.first
}

@Service
class SectionService(
    @Value("\${network.minimum-hold}") private val minimumHold: Int,
    private val switchService: SwitchService,
    private val signalService: SignalService,
    private val journeyRepo: JourneyRepo
) {

    private val queues = Queues(minimumHold, journeyRepo)
    private val sectionMonitor = SectionMonitor()

    suspend fun accept(transport: Transport, motionJob: Job, jobs: List<Job>?): Unit =
        coroutineScope {
            if (queues.getQueue(transport.section()).stream().anyMatch { it.id == transport.id }) {
                throw RuntimeException("${transport.id} being added twice to ${transport.section()}")
            }

            prepareRelease(transport) { t -> launch { release(t, motionJob, jobs) } }
        }

    suspend fun init(line: String): Unit = coroutineScope {
        queues.getQueueKeys().filter { it.first.contains(line) }.forEach {
            launch { signalService.init(it) }
            launch {
                sectionMonitor.monitor(it, queues.getChannel(it)) { k -> queues.getQueue(k.second).removeFirstOrNull()?.let { launch { arrive(k.first) } } }
            }
        }
    }

    fun isClear(section: Pair<String, String>, incoming: Boolean = false): Boolean =
        queues.isClear(section, incoming).first

    fun isClear(
        transport: Transport,
        switchFrom: Boolean,
        incoming: Boolean = false,
        approachingJunction: Boolean
    ): Boolean {
        val section = transport.section()
        val max =
            if (approachingJunction) 0 else if (transport.line.overrideIsClear) transport.line.transportersPerSection else 2
        val isSectionClear = queues.isClear(section, incoming, max).first
        val isTerminalSectionFromClear = if (switchFrom) {
            queues.isClear(
                section = Pair("${section.first}|", Line.getStation(section.first)),
                incoming = incoming,
                max = 0
            ).first
        } else {
            true
        }

        return isSectionClear && isTerminalSectionFromClear
    }

    fun isSwitchPlatform(transport: Transport, section: Pair<String, String>, destination: Boolean = false): Boolean =
        switchService.isSwitchPlatform(transport, section, destination)

    fun isSwitchSection(transport: Transport): Pair<Boolean, Boolean> =
        switchService.isSwitchSectionByTerminal(transport)

    fun initQueues(key: Pair<String, String>) = queues.initQueues(key)

    fun arePreviousSectionsClear(
        transport: Transport,
        lineInstructions: LineInstructions,
        sections: (Pair<String, String>) -> List<Pair<String, String>>
    ): Boolean {
        var isClear = true
        val line = transport.line.name
        val platformToKey = Pair("$line:${lineInstructions.direction}", "$line:${lineInstructions.to.id}")

        outer@ for (key in sections(platformToKey)) {
            if (!queues.isClear(key, true).first) {
                isClear = false
                break@outer
            }
        }
        return isClear
    }

    fun isStationTerminal(station: String): Boolean = switchService.isStationTerminal(station)
    fun dump() {
        queues.getQueueKeys().forEach { k ->
            if (queues.getQueue(k).isNotEmpty()) {
                queues.getQueue(k).forEach { t ->
                    println("$k contains ${t.id}")
                }
            }
        }
    }

    private suspend fun arrive(transport: Transport) = coroutineScope {
        journeyRepo.addJourneyTime(transport.getJourneyTime())
        launch { transport.arrived() }
    }

    private suspend fun release(transport: Transport, motionJob: Job, jobs: List<Job>?) = coroutineScope {
        val job =
            launch { transport.signal(signalService.getChannel(transport.section())!!) { t -> launch { departedActions(t) } } }

        if (switchService.isSwitchSection(transport)) {
            launch {
                switchService.switch(transport, listOf(job, motionJob)) {
                    launch { processSwitch(it) }
                }
            }
        }

        jobs?.forEach { it.cancel() }
    }

    private suspend fun departedActions(transport: Transport) {
        signalService.send(
            transport.platformKey(),
            SignalMessage(
                signalValue = SignalValue.GREEN,
                id = transport.id,
                key = transport.section(),
                line = transport.line.id,
                commuterChannel = transport.carriage.channel
            )
        )
    }

    private suspend fun processSwitch(details: Pair<Transport, Pair<String, String>>) = coroutineScope {
        val transport = details.first
        val sectionLeft = details.second
        val sectionEntering = transport.section()

        launch {
            transport.signal(signalService.getChannel(sectionEntering)!!) { t ->
                launch {
                    if (sectionEntering.second.contains("|")) {
                        SignalMessage(
                            signalValue = SignalValue.GREEN,
                            id = t.id,
                            key = sectionLeft,
                            line = t.line.id,
                            commuterChannel = t.carriage.channel
                        ).also {
                            signalService.send(
                                key = t.getMainlineForSwitch(),
                                signalMessage = it
                            )
                            signalService.send(
                                key = t.section(),
                                signalMessage = it
                            )
                        }
                    }
                }
            }
        }

        prepareRelease(transport) {
            launch {
                queues.getQueue(sectionLeft).removeFirstOrNull()
            }
        }
    }

    private suspend fun prepareRelease(
        transport: Transport,
        releaseConsumer: Consumer<Transport>
    ) = coroutineScope {
        queues.release(transport.section(), transport)
        transport.setChannel(queues.getChannel(transport.section()))
        releaseConsumer.accept(transport)
    }
}
