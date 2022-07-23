package com.tabiiki.kotlinlab.monitor

import com.tabiiki.kotlinlab.model.Transport
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import java.util.function.Consumer

class SectionMonitor {

    suspend fun monitor(
        key: Pair<String, String>,
        channel: Channel<Transport>,
        queueConsumer: Consumer<Pair<String, String>>
    ) = coroutineScope {
        do {
            val msg = channel.receive()
            when (msg.atPlatform()) {
                true -> queueConsumer.accept(key)
                else -> {}
            }

        } while (true)
    }
}