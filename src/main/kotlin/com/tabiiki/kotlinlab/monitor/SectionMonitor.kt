package com.tabiiki.kotlinlab.monitor

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import java.util.function.Consumer

enum class SectionMessage {
    ARRIVED
}

class SectionMonitor {

    suspend fun monitor(
        key: Pair<String, String>,
        channel: Channel<SectionMessage>,
        queueConsumer: Consumer<Pair<String, String>>
    ) = coroutineScope {
        do {
            when (channel.receive()) {
                SectionMessage.ARRIVED -> queueConsumer.accept(key)
            }

        } while (true)
    }
}