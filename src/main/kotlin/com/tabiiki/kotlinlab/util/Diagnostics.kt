package com.tabiiki.kotlinlab.util

import com.tabiiki.kotlinlab.model.Transport
import com.tabiiki.kotlinlab.monitor.PlatformMonitor
import com.tabiiki.kotlinlab.service.SectionServiceImpl
import kotlinx.coroutines.channels.Channel
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

class Diagnostics {
    fun dump(
        platformMonitor: PlatformMonitor? = null,
        queues: ConcurrentHashMap<Pair<String, String>, Pair<Channel<Transport>, ArrayDeque<Transport>>>? = null
    ) {

        platformMonitor?.getPlatformKeys()?.forEach { queue ->
            platformMonitor.atPlatform(queue).ifPresent {
                log.info("${it.id} current instruction ${it.line.id} ${it.getCurrentInstruction()} in $queue")
            }
        }

        queues?.keys()!!.toList().forEach { queue ->
            queues[queue]!!.second.forEach {
                log.info("${it.id} current instruction ${it.line.id} ${it.getCurrentInstruction()} in ${it.section()} ${it.getPosition()}")
            }
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(this.javaClass)
    }
}