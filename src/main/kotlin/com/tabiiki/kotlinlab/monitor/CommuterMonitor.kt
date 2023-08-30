package com.tabiiki.kotlinlab.monitor

import com.tabiiki.kotlinlab.model.Commuter
import kotlinx.coroutines.channels.Channel

class CommuterMonitor {

    suspend fun monitor(channel: Channel<Commuter>) {
        do {
            val msg = channel.receive() // what to do with it?  for now nothing.
        } while (true)
    }
}
