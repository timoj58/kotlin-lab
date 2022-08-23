package com.tabiiki.kotlinlab.util

import com.tabiiki.kotlinlab.monitor.PlatformMonitor
import com.tabiiki.kotlinlab.service.SectionServiceImpl
import org.slf4j.LoggerFactory

//one class to control this is cleaner.  TODO. also make it controlled  by param.  ie debug mode etc.
//add signals too.  this should do lots of things in future.  for now its just moved out of the way.
class Diagnostics {
    fun dump(
        platformMonitor: PlatformMonitor? = null,
        queues: SectionServiceImpl.Companion.Queues? = null
    ) {

        platformMonitor?.getPlatformKeys()?.forEach { queue ->
            platformMonitor?.atPlatform(queue)?.ifPresent {
                log.info("${it.id} current instruction ${it.line.id} ${it.getCurrentInstruction()} in $queue")
            }
        }

        queues?.getQueueKeys()?.forEach { queue ->
            queues?.getQueue(queue)?.forEach {
                log.info("${it.id} current instruction ${it.line.id} ${it.getCurrentInstruction()} in ${it.section()} ${it.getPosition()}")
            }
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(this.javaClass)
    }
}