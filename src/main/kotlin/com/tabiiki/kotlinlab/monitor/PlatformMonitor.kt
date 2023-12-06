package com.tabiiki.kotlinlab.monitor

import com.tabiiki.kotlinlab.factory.SignalMessage
import com.tabiiki.kotlinlab.factory.SignalValue
import com.tabiiki.kotlinlab.service.SignalService
import kotlinx.coroutines.coroutineScope
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

data class PlatformLockInfo(
    val clear: AtomicBoolean,
    var lockOwner: UUID? = null
) {
    fun update(lockOwner: UUID? = null, key: Pair<String, String>) {
        this.clear.set(lockOwner == null)
        this.lockOwner = lockOwner
        //   println("lock $lockOwner for $key")
    }
}

private class Platforms {

    private val platformLocks: ConcurrentHashMap<Pair<String, String>, PlatformLockInfo> = ConcurrentHashMap()

    fun init(key: Pair<String, String>) {
        platformLocks[key] = PlatformLockInfo(
            clear = AtomicBoolean(true)
        )
    }

    fun get(key: Pair<String, String>): UUID = platformLocks[key]!!.lockOwner!!

    fun isClear(key: Pair<String, String>): Boolean =
        (platformLocks[key] ?: throw Exception("missing $key")).clear.get() && platformLocks[key]!!.lockOwner == null
    fun getPlatformKeys(): List<Pair<String, String>> = platformLocks.keys().toList()
    fun lock(signal: SignalMessage, key: Pair<String, String>) {
        signal.id ?: return
        val new = when (signal.signalValue) {
            SignalValue.GREEN -> true
            SignalValue.RED -> false
        }

        if (new && (
            platformLocks[key]!!.lockOwner
                ?: signal.id
            ) != signal.id
        ) {
            throw RuntimeException("${signal.signalValue}:${signal.origin} - ${signal.id} is not owner of $key lock, ${platformLocks[key]}")
        }
        if (platformLocks[key]!!.lockOwner != null && platformLocks[key]!!.clear.acquire == new) throw RuntimeException("setting $key to same signal $signal.signalValue")
        when (new) {
            true -> platformLocks[key]!!.update(key = key)
            false -> platformLocks[key]!!.update(lockOwner = signal.id, key = key)
        }
    }
}

class PlatformMonitor(
    private val signalService: SignalService
) {
    private val platforms = Platforms()
    fun getPlatformKeys(): List<Pair<String, String>> = platforms.getPlatformKeys()
    fun isClear(key: Pair<String, String>): Boolean = platforms.isClear(key)

    fun getOwner(key: Pair<String, String>): UUID = platforms.get(key)
    fun init(key: Pair<String, String>) = platforms.init(key)
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
}
