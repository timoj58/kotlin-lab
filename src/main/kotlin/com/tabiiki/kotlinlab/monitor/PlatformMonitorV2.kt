package com.tabiiki.kotlinlab.monitor

import com.tabiiki.kotlinlab.service.SignalService
import kotlinx.coroutines.coroutineScope

class PlatformMonitorV2(
    private val signalService: SignalService
) {

    suspend fun monitor(key: Pair<String, String>) = coroutineScope {
        do {
            signalService.receive(key)?.let {
            }
        } while (true)
    }
}
