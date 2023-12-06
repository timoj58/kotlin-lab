package com.tabiiki.kotlinlab.service

import com.tabiiki.kotlinlab.factory.Origin
import com.tabiiki.kotlinlab.factory.SignalMessage
import com.tabiiki.kotlinlab.factory.SignalValue
import com.tabiiki.kotlinlab.model.Line
import com.tabiiki.kotlinlab.model.Transport
import com.tabiiki.kotlinlab.monitor.SectionMonitor
import com.tabiiki.kotlinlab.repo.JourneyRepo
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Consumer

private class Queues {
    val queues: ConcurrentHashMap<Pair<String, String>, Pair<Channel<Transport>, ArrayDeque<Transport>>> =
        ConcurrentHashMap()

    fun getQueue(key: Pair<String, String>): ArrayDeque<Transport> = queues[key]!!.second

    fun initQueues(key: Pair<String, String>) {
        queues[key] = Pair(Channel(), ArrayDeque())
    }

    fun isQueueClear(section: Pair<String, String>): Boolean =
        queues[section]!!.second.isEmpty()

    fun getQueueKeys(): List<Pair<String, String>> = queues.keys().toList()

    fun release(key: Pair<String, String>, transport: Transport) {
        val limit = 1
        if (queues[key]!!.second.size > limit) throw RuntimeException("${transport.id} Only $limit transporters ${queues[key]!!.second.map { it.id }} allowed in $key")
        queues[key]!!.second.addLast(transport)
    }

    fun getChannel(key: Pair<String, String>): Channel<Transport> = queues[key]!!.first
}

@Service
class SectionService(
    private val switchService: SwitchService,
    private val signalService: SignalService,
    private val journeyRepo: JourneyRepo
) {

    private val queues = Queues()
    private val sectionMonitor = SectionMonitor()

    suspend fun accept(transport: Transport, motionJob: Job, jobs: List<Job>?): Unit =
        coroutineScope {
            if (queues.getQueue(transport.section()).stream().anyMatch { it.id == transport.id }) {
                throw RuntimeException("${transport.id} being added twice to ${transport.section()}")
            }

            prepareRelease(transport) { t -> launch { release(t, motionJob, jobs) } }
        }

    suspend fun init(line: String, holdConsumer: Consumer<Transport>): Unit = coroutineScope {
        queues.getQueueKeys().filter { it.first.contains(line) }.forEach {
            launch { signalService.init(it) }
            launch {
                sectionMonitor.monitor(it, queues.getChannel(it)) { k ->
                    queues.getQueue(k.second).removeFirstOrNull()?.let {
                        journeyRepo.addJourneyTime(k.first.getJourneyTime())
                        holdConsumer.accept(k.first)
                    }
                }
            }
        }
    }

    fun isSectionClear(transport: Transport, isTerminalSection: Boolean): Boolean {
        val isClear = queues.isQueueClear(section = transport.section())

        return when (isTerminalSection) {
            true -> isClear && queues.isQueueClear(
                section = Pair("${transport.section().first}|", Line.getStation(transport.section().first))
            )
            false -> isClear
        }
    }

    fun isSwitchPlatform(transport: Transport, section: Pair<String, String>, destination: Boolean = false): Boolean =
        switchService.isSwitchPlatform(transport, section, destination)

    fun initQueues(key: Pair<String, String>) = queues.initQueues(key)

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
                commuterChannel = transport.carriage.channel,
                origin = Origin.DEPART
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
                            commuterChannel = t.carriage.channel,
                            origin = Origin.SWITCH
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
