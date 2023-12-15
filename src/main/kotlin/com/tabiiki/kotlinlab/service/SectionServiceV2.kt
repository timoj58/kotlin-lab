package com.tabiiki.kotlinlab.service

import com.tabiiki.kotlinlab.factory.SignalMessageV2
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
    private val switchService: SwitchService
) {
    val channels: ConcurrentHashMap<Pair<String, String>, Channel<Transport>> =
        ConcurrentHashMap()

    suspend fun accept(
        transport: Transport,
        motionJob: Job,
        jobs: List<Job>?,
        sectionSubscription: Channel<SignalMessageV2>,
        switchActions: Consumer<Triple<Transport, Pair<String, String>, Pair<String, String>>>
    ): Unit = coroutineScope {
        launch {
            release(
                transport = transport,
                motionJob = motionJob,
                jobs = jobs,
                sectionSubscription = sectionSubscription,
                switchActions = switchActions
            )
        }
    }

    fun isSwitchPlatform(transport: Transport, section: Pair<String, String>, destination: Boolean = false): Boolean =
        switchService.isSwitchPlatform(transport, section, destination)

    private suspend fun release(
        transport: Transport,
        motionJob: Job,
        jobs: List<Job>?,
        sectionSubscription: Channel<SignalMessageV2>,
        switchActions: Consumer<Triple<Transport, Pair<String, String>, Pair<String, String>>>
    ) = coroutineScope {
        // println("${transport.id} entering ${transport.section()}")
        val job = launch {
            transport.monitorSectionSignal(sectionSubscription = sectionSubscription) { }
        }

        if (switchService.isSwitchSection(transport)) {
            launch {
                switchService.switch(transport, listOf(job, motionJob)) {
                    launch {
                        processSwitch(
                            details = it,
                            switchActions = switchActions
                        )
                    }
                }
            }
        }

        jobs?.forEach { it.cancel() }
    }

    private suspend fun processSwitch(
        details: Pair<Transport, Pair<String, String>>,
        switchActions: Consumer<Triple<Transport, Pair<String, String>, Pair<String, String>>>
    ) = coroutineScope {
        val transport = details.first
        val sectionLeft = details.second
        val sectionEntering = transport.section()

        switchActions.accept(Triple(transport, sectionLeft, sectionEntering))
    }
}
