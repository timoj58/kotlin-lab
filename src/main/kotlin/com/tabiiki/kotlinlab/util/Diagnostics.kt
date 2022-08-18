package com.tabiiki.kotlinlab.util

import com.tabiiki.kotlinlab.model.Transport
import com.tabiiki.kotlinlab.monitor.PlatformMonitor
import com.tabiiki.kotlinlab.service.SectionServiceImpl
import org.slf4j.LoggerFactory
import java.util.UUID

//one class to control this is cleaner.  TODO. also make it controlled  by param.  ie debug mode etc.
//add signals too.  this should do lots of things in future.  for now its just moved out of the way.
class Diagnostics {
    fun dump(
        platformMonitor: PlatformMonitor,
        queues: SectionServiceImpl.Companion.Queues,
        transports: List<UUID>?
    ) {
        val items = mutableListOf<Transport.Companion.JournalRecord>()

        platformMonitor.getPlatformKeys().forEach { queue ->
            platformMonitor.atPlatform(queue).ifPresent {
                log.info("${it.id} current instruction ${it.line.id} ${it.getCurrentInstruction()} in $queue")
            }
        }

        queues.getQueueKeys().forEach { queue ->
            queues.getQueue(queue).forEach {
                log.info("${it.id} current instruction ${it.line.id} ${it.getCurrentInstruction()} in ${it.section()} ${it.getPosition()}")
            }
        }

        platformMonitor.getPlatformKeys().forEach { queue ->
            val toAdd = platformMonitor.atPlatform(queue)
                .filter { t -> transports == null || transports.contains(t.id) }
                .map { m -> m.journal.getLog().sortedByDescending { l -> l.milliseconds }.take(5) }
            toAdd.ifPresent {
                items.addAll(it)
            }
        }

        queues.getQueueKeys().forEach { queue ->
            val toAdd = queues.getQueue(queue)
                .filter { t -> transports == null || transports.contains(t.id) }
                .map { m -> m.journal.getLog().sortedByDescending { l -> l.milliseconds }.take(5) }
                .flatten()
            items.addAll(toAdd)
        }

        items.sortedByDescending { it.milliseconds }.forEach { log.info(it.print()) }
    }

    companion object {
        private val log = LoggerFactory.getLogger(this.javaClass)
    }
}