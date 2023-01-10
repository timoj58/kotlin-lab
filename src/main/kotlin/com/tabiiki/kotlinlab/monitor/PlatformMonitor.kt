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
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Consumer

private class Platforms {
    private val platforms: ConcurrentHashMap<Pair<String, String>, AtomicBoolean> =
        ConcurrentHashMap()

    private val platformLockOwners: ConcurrentHashMap<Pair<String, String>, UUID> =
        ConcurrentHashMap()

    fun init(key: Pair<String, String>) {
        platforms[key] = AtomicBoolean(true)
    }

    fun isClear(key: Pair<String, String>): Boolean = (platforms[key] ?: throw Exception("missing $key")).get()
    fun getPlatformKeys(): List<Pair<String, String>> = platforms.keys().toList()
    fun lock(signal: SignalMessage, key: Pair<String, String>) {
        signal.id ?: return
        val new = when (signal.signalValue) {
            SignalValue.GREEN -> true
            SignalValue.RED -> false
        }
        if (new && (platformLockOwners[key]
                ?: signal.id) != signal.id
        ) throw RuntimeException("${signal.id} is not owner of $key lock, ${platformLockOwners[key]}")

        val current = platforms[key]!!.acquire
        if (platformLockOwners[key] != null && current == new) throw RuntimeException("setting $key to same signal $signal.signalValue")
        if (!new) platformLockOwners[key] = signal.id else platformLockOwners.remove(key)
        platforms[key]!!.set(new)
       // println(" ${signal.signalValue} for $key by ${signal.id}")
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
            "no channel for ${transport.id} ${transport.line.id} ${platformToKey(transport)} ${transport.section()}"
        )

    suspend fun monitorPlatform(key: Pair<String, String>) = coroutineScope {
        var previousSignal: SignalMessage? = null
        var terminalSection: Pair<String, String>? = null

        if (key.first.contains(LineDirection.TERMINAL.toString()))
            terminalSection = terminalSection(key)

        do {
            signalService.receive(key)?.let {
                if (previousSignal == null || previousSignal != it) {
                    platforms.lock(it, key)

                    when (it.signalValue) {
                        SignalValue.RED -> launch { processRed(it, key, terminalSection) }
                        SignalValue.GREEN -> launch { processGreen(it, key, terminalSection) }
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
                holdConsumer.accept(msg)
            }
        } while (true)
    }

    private suspend fun processGreen(
        signal: SignalMessage,
        key: Pair<String, String>,
        terminalSection: Pair<String, String>?
    ) =
        coroutineScope {

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
           //     println("GREEN (platform $key) - ${it.first} by ${signal.id}")
                signalService.send(
                    key = it.first,
                    signalMessage = SignalMessage(signalValue = signal.signalValue, key = signal.key, id = signal.id)
                )
            }

            sections.filter { priority == null || it.second.first }.forEach { section ->
             //   println("GREEN (platform $key) - ${section.first} by ${signal.id}")

                if(sectionService.isStationTerminal(section.first.first))
                    signalService.send(
                        key = Pair("${section.first.first}|", Line.getStation(section.first.first)),
                        signalMessage = SignalMessage(signalValue = signal.signalValue, key = signal.key, id = signal.id)
                    )

                signalService.send(
                    key = section.first,
                    signalMessage = SignalMessage(signalValue = signal.signalValue, key = signal.key, id = signal.id)
                )
            }
        }

    private suspend fun processRed(
        signal: SignalMessage,
        key: Pair<String, String>,
        terminalSection: Pair<String, String>?
    ) =
        coroutineScope {
            val sections: List<Pair<String, String>> =
                if (terminalSection != null) listOf(terminalSection) else lineRepo.getPreviousSections(key)

            sections.forEach {
             //   println("RED (platform $key) - $it by ${signal.id}")
                if(sectionService.isStationTerminal(it.first))
                    signalService.send(
                        key = Pair("${it.first}|", Line.getStation(it.first)),
                        signalMessage = SignalMessage(signalValue = signal.signalValue, key = signal.key, id = signal.id)
                    )

                signalService.send(
                    key = it,
                    signalMessage = SignalMessage(
                        signalValue = signal.signalValue,
                        key = signal.key,
                        id = signal.id,
                    )
                )
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