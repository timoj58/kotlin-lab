package com.tabiiki.kotlinlab.service

import com.tabiiki.kotlinlab.factory.Origin
import com.tabiiki.kotlinlab.factory.SignalMessage
import com.tabiiki.kotlinlab.factory.SignalValue
import com.tabiiki.kotlinlab.model.Transport
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Consumer

private class QueuesV2 {
    val queues: ConcurrentHashMap<Pair<String, String>, Pair<Channel<Transport>, ArrayDeque<Transport>>> =
        ConcurrentHashMap()

    fun getQueue(key: Pair<String, String>): ArrayDeque<Transport> = queues[key]!!.second

    fun initQueues(key: Pair<String, String>) {
        queues[key] = Pair(Channel(), ArrayDeque())
    }

    fun getQueueKeys(): List<Pair<String, String>> = queues.keys().toList()

    fun release(key: Pair<String, String>, transport: Transport) {
        val limit = 1
        if (queues[key]!!.second.size > limit) throw RuntimeException("${transport.id} Only $limit transporters ${queues[key]!!.second.map { it.id }} allowed in $key")
        queues[key]!!.second.addLast(transport)
    }

    fun getChannel(key: Pair<String, String>): Channel<Transport> = queues[key]!!.first
}

@Service
class SectionServiceV2(
    private val switchService: SwitchService,
    private val signalService: SignalService
) {
    private val queues = QueuesV2()

    init {
        signalService.getSectionSignals().forEach { queues.initQueues(it) }
    }

    suspend fun init() = coroutineScope {
        queues.getQueueKeys().forEach { launch { signalService.init(it) } }
    }

    suspend fun accept(transport: Transport, motionJob: Job, jobs: List<Job>?): Unit = coroutineScope {
        if (queues.getQueue(transport.section()).stream().anyMatch { it.id == transport.id }) {
            throw RuntimeException("${transport.id} being added twice to ${transport.section()}")
        }
        prepareRelease(transport) { t -> launch { release(t, motionJob, jobs) } }
    }

    fun removeFromQueue(key: Pair<String, String>) {
        queues.getQueue(key).removeFirstOrNull()?.let {
            println("removing from queue $key")
        }
    }

    fun isSwitchPlatform(transport: Transport, section: Pair<String, String>, destination: Boolean = false): Boolean =
        switchService.isSwitchPlatform(transport, section, destination)

    private suspend fun prepareRelease(
        transport: Transport,
        releaseConsumer: Consumer<Transport>
    ) = coroutineScope {
        queues.release(transport.section(), transport)
        transport.setChannel(queues.getChannel(transport.section()))
        releaseConsumer.accept(transport)
    }

    private suspend fun release(transport: Transport, motionJob: Job, jobs: List<Job>?) = coroutineScope {
        println("departing ${transport.id} ${transport.section()}")
        val lastMessage = signalService.getLastMessage(transport.section())
        val job =
            launch {
                transport.signal(signalService.getChannel(transport.section())!!) { }
                lastMessage?.let {
                    println("${transport.id} sending latest message $it")
                    signalService.send(
                        transport.section(),
                        it.also { msg ->
                            msg.timesStamp = System.currentTimeMillis()
                        }
                    )
                }
            }

        if (switchService.isSwitchSection(transport)) {
            launch {
                switchService.switch(transport, listOf(job, motionJob)) {
                    launch { processSwitch(it) }
                }
            }
        }

        jobs?.forEach { it.cancel() }
    }

    private suspend fun processSwitch(details: Pair<Transport, Pair<String, String>>) = coroutineScope {
        val transport = details.first
        val sectionLeft = details.second
        val sectionEntering = transport.section()
        val lastMessage = signalService.getLastMessage(sectionEntering)

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
                    lastMessage?.let {
                        println("sending latest switch message")
                        signalService.send(
                            sectionEntering,
                            it.also { msg ->
                                msg.timesStamp = System.currentTimeMillis()
                            }
                        )
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
}
