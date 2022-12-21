package com.tabiiki.kotlinlab.util

import com.tabiiki.kotlinlab.model.Transport
import kotlinx.coroutines.channels.Channel
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

class Diagnostics {
    fun dump(
        queues: ConcurrentHashMap<Pair<String, String>, Pair<Channel<Transport>, ArrayDeque<Transport>>>? = null
    ) {
        queues?.keys()?.let {
            it.toList().forEach { queue ->
                queues[queue]!!.second.forEach {
                    log.info("QUEUE: ${it.id} current instruction ${it.line.id} ${it.getCurrentInstruction()} in ${it.section()} ${it.getPosition()}")
                }
            }
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(this.javaClass)
    }
}