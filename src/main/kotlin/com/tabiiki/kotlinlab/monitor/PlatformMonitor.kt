package com.tabiiki.kotlinlab.monitor

import com.tabiiki.kotlinlab.factory.SignalMessage
import com.tabiiki.kotlinlab.factory.SignalValue
import com.tabiiki.kotlinlab.model.Transport
import com.tabiiki.kotlinlab.repo.LineDirection
import com.tabiiki.kotlinlab.repo.LineRepo
import com.tabiiki.kotlinlab.service.SectionService
import com.tabiiki.kotlinlab.service.SignalService
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Consumer


class PlatformMonitor(
    private val sectionService: SectionService,
    private val signalService: SignalService,
    private val lineRepo: LineRepo
) {
    private val platforms = Platforms()
    fun getPlatformKeys(): List<Pair<String, String>> = platforms.getPlatformKeys()
    fun isClear(key: Pair<String, String>): Boolean = platforms.isClear(key)
    fun init(key: Pair<String, String>) = platforms.init(key)
    fun atPlatform(key: Pair<String, String>): Optional<Transport> = platforms.atPlatform(key)
    fun accept(key: Pair<String, String>, transport: Transport) = platforms.accept(key, transport)
    fun release(key: Pair<String, String>, transport: Transport) = platforms.release(key, transport)
    fun getHoldChannel(transport: Transport): Channel<Transport> = holdChannels[platformToKey(transport)]!!

    suspend fun monitorPlatform(key: Pair<String, String>) = coroutineScope {
        var previousSignal: SignalMessage? = null
        val isTerminal = key.first.contains(LineDirection.TERMINAL.toString())
        val terminalSection = terminalSection(key)

        do {
            signalService.receive(key)?.let {
                if (previousSignal == null || it != previousSignal) {
                    previousSignal = it

                    when (it.signalValue) {
                        SignalValue.RED -> processRed(it, key, isTerminal, terminalSection)
                        SignalValue.GREEN -> processGreen(it, key, isTerminal, terminalSection)
                    }
                }
            }

        } while (true)
    }

    suspend fun monitorPlatformHold(key: Pair<String, String>, holdConsumer: Consumer<Transport>) = coroutineScope {
        val channel = Channel<Transport>()
        holdChannels[key] = channel
        do {
            val msg = channel.receive()
            platformToKey(msg).let {
                val atPlatform = platforms.atPlatform(it)
                if (!atPlatform.isEmpty) {
                    throw RuntimeException(
                        "${msg.id} arrived too quickly $it , already holding ${atPlatform.get().id} "
                    )
                }
            }
            holdConsumer.accept(msg)
        } while (true)
    }

    private suspend fun processGreen(
        signal: SignalMessage,
        key: Pair<String, String>,
        isTerminal: Boolean,
        terminalSection: Pair<String, String>
    ) =
        coroutineScope {
            val sections = if (isTerminal) listOf(
                Pair(
                    terminalSection,
                    sectionService.isClearWithPriority(terminalSection)
                )
            )
            else lineRepo.getPreviousSections(key).map { Pair(it, sectionService.isClearWithPriority(it)) }
            val priority = sections.filter { it.second.second != 0 }.sortedByDescending { it.second.second }
                .firstOrNull { !it.second.first }

            priority?.let {
                launch {
                    signalService.send(
                        it.first,
                        SignalMessage(signalValue = SignalValue.GREEN, key = signal.key)
                    )
                }
            }

            sections.filter { priority == null || it.second.first }.forEach { section ->
                launch {
                    signalService.send(
                        section.first,
                        SignalMessage(signalValue = SignalValue.GREEN, key = signal.key)
                    )
                }
            }
        }

    private suspend fun processRed(
        signal: SignalMessage,
        key: Pair<String, String>,
        isTerminal: Boolean,
        terminalSection: Pair<String, String>
    ) =
        coroutineScope {
            val sections = if (isTerminal) listOf(terminalSection) else lineRepo.getPreviousSections(key)
            sections.forEach {
                launch {
                    signalService.send(
                        it,
                        SignalMessage(signalValue = SignalValue.RED, key = signal.key, id = signal.id)
                    )
                }
            }
        }

    private fun platformToKey(transport: Transport): Pair<String, String> {
        var switchStation = false
        if (!transport.platformKey().first.contains(LineDirection.TERMINAL.toString()))
            switchStation = sectionService.isSwitchPlatform(transport, transport.getJourneyTime().first, true)

        return transport.platformToKey(switchStation)!!
    }

    companion object {
        private val holdChannels: ConcurrentHashMap<Pair<String, String>, Channel<Transport>> = ConcurrentHashMap()

        private fun terminalSection(key: Pair<String, String>) = Pair(
            "${key.first.substringBefore(":")}:${key.second.substringAfter(":")}",
            "${key.second.substringAfter(":")}|"
        )

        class Platforms {
            private val platforms: ConcurrentHashMap<Pair<String, String>, AtomicReference<Optional<Transport>>> =
                ConcurrentHashMap()

            fun init(key: Pair<String, String>) {
                platforms[key] = AtomicReference(Optional.empty())
            }

            fun isClear(key: Pair<String, String>): Boolean = platforms[key]?.get()?.isEmpty ?: true
            fun getPlatformKeys(): List<Pair<String, String>> = platforms.keys().toList()

            fun accept(key: Pair<String, String>, transport: Transport) {
                if (!platforms[key]!!.get().isEmpty) {
                    throw RuntimeException(
                        "FATAL - already holding ${
                            platforms[key]!!.get().get().id
                        } for $key next ${transport.id}"
                    )
                }
                platforms[key]!!.set(Optional.of(transport))
            }

            fun release(key: Pair<String, String>, transport: Transport) {
                platforms[key]!!.set(Optional.empty())
                transport.journal.add(
                    Transport.Companion.JournalRecord(
                        action = Transport.Companion.JournalActions.READY_TO_DEPART, key = key, signal = SignalValue.RED
                    )
                )
            }
            fun atPlatform(key: Pair<String, String>): Optional<Transport> = platforms[key]!!.get()
        }
    }
}