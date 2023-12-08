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

@Service
class SectionServiceV2(
    private val switchService: SwitchService,
    private val signalService: SignalService
) {
    val channels: ConcurrentHashMap<Pair<String, String>, Channel<Transport>> =
        ConcurrentHashMap()

    init {
        signalService.getSectionSignals().forEach {
            channels[it] = Channel()
        }
    }

    suspend fun init() = coroutineScope {
        channels.forEach { (k, _) ->
            launch { signalService.init(k) }
        }
    }

    suspend fun accept(transport: Transport, motionJob: Job, jobs: List<Job>?): Unit = coroutineScope {
        prepareRelease(transport) { t -> launch { release(t, motionJob, jobs) } }
    }

    fun isSwitchPlatform(transport: Transport, section: Pair<String, String>, destination: Boolean = false): Boolean =
        switchService.isSwitchPlatform(transport, section, destination)

    private suspend fun prepareRelease(
        transport: Transport,
        releaseConsumer: Consumer<Transport>
    ) = coroutineScope {
        transport.setChannel(channels[transport.section()]!!)
        releaseConsumer.accept(transport)
    }

    private suspend fun release(transport: Transport, motionJob: Job, jobs: List<Job>?) = coroutineScope {
        val lastMessage = signalService.getLastMessage(transport.section())
        val job =
            launch {
                transport.signal(signalService.getChannel(transport.section())!!) { }
                lastMessage?.let {
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

        prepareRelease(transport) {}
    }
}
