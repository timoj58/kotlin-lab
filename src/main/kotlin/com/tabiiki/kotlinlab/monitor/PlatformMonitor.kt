package com.tabiiki.kotlinlab.monitor

import com.tabiiki.kotlinlab.factory.SignalMessage
import com.tabiiki.kotlinlab.factory.SignalValue
import com.tabiiki.kotlinlab.model.Transport
import com.tabiiki.kotlinlab.repo.LineDirection
import com.tabiiki.kotlinlab.service.SectionService
import com.tabiiki.kotlinlab.service.SignalService
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
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

    fun get(key: Pair<String, String>) = platformLockOwners[key]!!

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
        //println(" ${signal.signalValue} for $key by ${signal.id}")
    }
}

class PlatformMonitor(
    private val sectionService: SectionService,
    private val signalService: SignalService,
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

        do {
            signalService.receive(key)?.let {
                if (previousSignal == null || previousSignal != it) {
                    platforms.lock(it, key)
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
                    throw RuntimeException(
                        "${msg.id} arrived too quickly from ${msg.getJourneyTime().first} $it, owner: ${
                            platforms.get(
                                it
                            )
                        }"
                    )
                }
                holdConsumer.accept(msg)
            }
        } while (true)
    }

    fun dump() {
        platforms.getPlatformKeys().forEach {
            if (!platforms.isClear(it))
                println("$it: holding ${platforms.get(it)}")
        }
    }

    private fun platformToKey(transport: Transport): Pair<String, String> {
        var switchStation = false
        if (!transport.platformKey().first.contains(LineDirection.TERMINAL.name))
            switchStation = sectionService.isSwitchPlatform(transport, transport.getJourneyTime().first, true)

        return transport.platformToKey(switchStation)!!
    }
}