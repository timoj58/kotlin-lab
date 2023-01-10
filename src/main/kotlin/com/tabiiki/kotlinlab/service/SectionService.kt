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

    //TODO for now disabled, ie one transporter per section only.
    fun isClear(section: Pair<String, String>, incoming: Boolean, max: Int = 0): Pair<Boolean, Int> = Pair(
        queues[section]!!.second.isEmpty()
                || (
                queues[section]!!.second.size < max
                        && journeyRepo.getJourneyTime(section, minimumHold + 1).first > minimumHold
                        && (if (incoming) incomingCheck(section) else defaultCheck(section))),
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
        val predictedDistance = (journey.second / journey.first) * minimumHold
        return if (!incoming) currentPosition > predictedDistance else currentPosition < predictedDistance
    }

    fun getQueueKeys(): List<Pair<String, String>> = queues.keys().toList()

    fun release(key: Pair<String, String>, transport: Transport) {
        //set to two for now, due to switch platform releases..other checks enforce no overlaps.
        //and intention is to put back multiple transporters per section soon.
        //and fix emirates given its stuck at 2 carts..TODO
        if (queues[key]!!.second.size >= 2) throw RuntimeException("${transport.id} Only one transporter ${queues[key]!!.second.map { it.id }} allowed in $key")
        queues[key]!!.second.addLast(transport)
    }

    fun getChannel(key: Pair<String, String>): Channel<Transport> = queues[key]!!.first
}


interface SectionService {
    suspend fun accept(transport: Transport, jobs: List<Job>?)
    suspend fun init(line: String)
    fun isClear(section: Pair<String, String>, incoming: Boolean = false): Boolean
    fun isClear(
        transport: Transport,
        switchFrom: Boolean,
      //  switchTo: Pair<Boolean, Boolean>,
        incoming: Boolean = false
    ): Boolean

    fun isClearWithPriority(section: Pair<String, String>): Pair<Boolean, Int>
    fun isSwitchPlatform(transport: Transport, section: Pair<String, String>, destination: Boolean = false): Boolean
    fun isSwitchSection(transport: Transport): Pair<Boolean, Boolean>
    fun initQueues(key: Pair<String, String>)
    fun areSectionsClear(
        transport: Transport,
        lineInstructions: LineInstructions,
        sections: (Pair<String, String>) -> List<Pair<String, String>>
    ): Boolean

    fun isStationTerminal(station: String): Boolean
}

@Service
class SectionServiceImpl(
    @Value("\${network.minimum-hold}") private val minimumHold: Int,
    private val switchService: SwitchService,
    private val signalService: SignalService,
    private val journeyRepo: JourneyRepo,
) : SectionService {

    private val queues = Queues(minimumHold, journeyRepo)
    private val sectionMonitor = SectionMonitor()

    override suspend fun accept(transport: Transport, jobs: List<Job>?): Unit =
        coroutineScope {
            if (queues.getQueue(transport.section()).stream().anyMatch { it.id == transport.id })
                throw RuntimeException("${transport.id} being added twice to ${transport.section()}")

            prepareRelease(transport) { t -> launch { release(t, jobs) } }
        }

    override suspend fun init(line: String): Unit = coroutineScope {
        queues.getQueueKeys().filter { it.first.contains(line) }.forEach {
            launch { signalService.init(it) }
            launch {
                sectionMonitor.monitor(it, queues.getChannel(it))
                { k -> queues.getQueue(k.second).removeFirstOrNull()?.let { launch { arrive(k.first) } } }
            }
        }
    }

    override fun isClear(section: Pair<String, String>, incoming: Boolean): Boolean =
        queues.isClear(section, incoming).first

    override fun isClear(
        transport: Transport,
        switchFrom: Boolean,
     //   switchTo: Pair<Boolean, Boolean>,
        incoming: Boolean
    ): Boolean {
        val section = transport.section()
        val max = 0 //only 1 per section now
        val isSectionClear = queues.isClear(section, incoming, max).first
        val isTerminalSectionFromClear = if (switchFrom) queues.isClear(
            section = Pair("${section.first}|", Line.getStation(section.first)),
            incoming = incoming,
            max = max
        ).first else true
        //TODO review this - works better without it.  was experiment,
  /*      val isTerminalSectionToClear = if (switchTo.first)
            queues.isClear(
                section = Pair(section.first, "${Line.getStation(section.first)}|"),
                incoming = incoming,
                max = max
            ).first
        else if (switchTo.second)
            queues.isClear(
                section = Pair("${transport.line.name}:${section.second}", "${Line.getStation(section.second)}|"),
                incoming = incoming,
                max = max
            ).first else true */

        return isSectionClear && isTerminalSectionFromClear //&& isTerminalSectionToClear
    }

    override fun isClearWithPriority(section: Pair<String, String>): Pair<Boolean, Int> =
        queues.isClear(section, true)

    override fun isSwitchPlatform(transport: Transport, section: Pair<String, String>, destination: Boolean): Boolean =
        switchService.isSwitchPlatform(transport, section, destination)

    override fun isSwitchSection(transport: Transport): Pair<Boolean, Boolean> =
        switchService.isSwitchSectionByTerminal(transport)

    override fun initQueues(key: Pair<String, String>) = queues.initQueues(key)

    override fun areSectionsClear(
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

    override fun isStationTerminal(station: String): Boolean = switchService.isStationTerminal(station)

    private suspend fun arrive(transport: Transport) = coroutineScope {
        journeyRepo.addJourneyTime(transport.getJourneyTime())
        launch { transport.arrived() }
    }

    private suspend fun release(transport: Transport, jobs: List<Job>?) = coroutineScope {
        val job = launch { transport.signal(signalService.getChannel(transport.section())!!) }

        if (switchService.isSwitchSection(transport))
            launch {
                switchService.switch(transport) {
                    launch { processSwitch(it, job) }
                }
            }
        launch {
            signalService.send(
                transport.platformKey(), SignalMessage(
                    signalValue = SignalValue.GREEN,
                    id = transport.id,
                    key = transport.section(),
                    line = transport.line.id,
                    commuterChannel = transport.carriage.channel,
                )
            )
            jobs?.forEach { it.cancel() }
        }
    }

    private suspend fun processSwitch(details: Pair<Transport, Pair<String, String>>, job: Job) = coroutineScope {
        val transport = details.first
        val sectionLeft = details.second

        job.cancel()

        prepareRelease(transport) { t ->
            launch {
                queues.getQueue(sectionLeft).removeFirstOrNull()
                transport.signal(signalService.getChannel(t.section())!!)
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