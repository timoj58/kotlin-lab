package com.tabiiki.kotlinlab.monitor

import com.tabiiki.kotlinlab.factory.SignalMessage
import com.tabiiki.kotlinlab.factory.SignalValue
import com.tabiiki.kotlinlab.model.Line
import com.tabiiki.kotlinlab.model.Transport
import com.tabiiki.kotlinlab.repo.LineDirection
import com.tabiiki.kotlinlab.repo.LineRepo
import com.tabiiki.kotlinlab.service.SectionService
import com.tabiiki.kotlinlab.service.SignalService
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Consumer


private class Platforms {
    private val platforms: ConcurrentHashMap<Pair<String, String>, AtomicBoolean> =
        ConcurrentHashMap()

    fun init(key: Pair<String, String>) {
        platforms[key] = AtomicBoolean(false)
    }

    fun isClear(key: Pair<String, String>): Boolean = (platforms[key] ?: throw Exception("missing $key")).get()
    fun getPlatformKeys(): List<Pair<String, String>> = platforms.keys().toList()
    fun lock(signalValue: SignalValue, key: Pair<String, String>) {
        val new = when (signalValue) {
            SignalValue.GREEN -> true
            SignalValue.RED -> false
        }
        val current = platforms[key]!!.acquire
        if (current == new) throw RuntimeException("setting $key to same signal $signalValue")
        platforms[key]!!.set(new)
    }
}


class PlatformMonitor(
    private val sectionService: SectionService,
    private val signalService: SignalService,
    private val lineRepo: LineRepo
) {
    private val holdChannels: ConcurrentHashMap<Pair<String, String>, Channel<Transport>> = ConcurrentHashMap()
    private val platforms = Platforms()
    fun getPlatformKeys(): List<Pair<String, String>> = platforms.getPlatformKeys()
    fun isClear(key: Pair<String, String>): Boolean = platforms.isClear(key)
    fun init(key: Pair<String, String>) = platforms.init(key)
    fun getHoldChannel(transport: Transport): Channel<Transport> =
        holdChannels[platformToKey(transport)] ?: throw Exception(
            "no channel for ${transport.id} ${
                platformToKey(transport)
            }"
        )

    suspend fun monitorPlatform(key: Pair<String, String>) = coroutineScope {
        var previousSignal: SignalMessage? = null
        var terminalSection: Pair<String, String>? = null

        if (key.first.contains(LineDirection.TERMINAL.toString()))
            terminalSection = terminalSection(key)

        do {
            signalService.receive(key)?.let {
                if (previousSignal == null || it != previousSignal) {
                    platforms.lock(it.signalValue, key)

                    when (it.signalValue) {
                        SignalValue.RED -> processRed(it, key, terminalSection)
                        SignalValue.GREEN -> processGreen(it, key, terminalSection)
                    }

                    previousSignal = it
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
                if (!platforms.isClear(it)) {
                    throw RuntimeException("${msg.id} arrived too quickly from ${msg.getJourneyTime().first} $it")
                }
                launch { holdConsumer.accept(msg) }
            }
        } while (true)
    }

    private suspend fun processGreen(
        signal: SignalMessage,
        key: Pair<String, String>,
        terminalSection: Pair<String, String>?
    ) =
        coroutineScope {
            val author = "PLATFORM_MONITOR - ${SignalValue.GREEN}"
            val sections: List<Pair<Pair<String, String>, Pair<Boolean, Int>>> = if (terminalSection != null)
                listOf(
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
                        SignalMessage(signalValue = SignalValue.GREEN, key = signal.key, producer = author)
                    )
                }
            }

            sections.filter { priority == null || it.second.first }.forEach { section ->
                launch {
                    signalService.send(
                        section.first,
                        SignalMessage(signalValue = SignalValue.GREEN, key = signal.key, producer = author)
                    )
                }
            }
        }

    private suspend fun processRed(
        signal: SignalMessage,
        key: Pair<String, String>,
        terminalSection: Pair<String, String>?
    ) =
        coroutineScope {
            val author = "PLATFORM_MONITOR - ${SignalValue.RED}"
            val sections: List<Pair<String, String>> =
                if (terminalSection != null) listOf(terminalSection) else lineRepo.getPreviousSections(key)

            sections.forEach {
                launch {
                    signalService.send(
                        it,
                        SignalMessage(
                            signalValue = SignalValue.RED,
                            key = signal.key,
                            id = signal.id,
                            producer = author
                        )
                    )
                }
            }
        }

    private fun platformToKey(transport: Transport): Pair<String, String> {
        var switchStation = false
        if (!transport.platformKey().first.contains(LineDirection.TERMINAL.name))
            switchStation = sectionService.isSwitchPlatform(transport, transport.getJourneyTime().first, true)

        return transport.platformToKey(switchStation)!!
    }

    companion object {
        private fun terminalSection(key: Pair<String, String>) = Pair(
            "${Line.getLine(key.first)}:${Line.getStation(key.second)}",
            "${Line.getStation(key.second)}|"
        )
    }
}