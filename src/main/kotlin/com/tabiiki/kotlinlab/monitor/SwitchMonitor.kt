package com.tabiiki.kotlinlab.monitor

import com.tabiiki.kotlinlab.model.Line
import com.tabiiki.kotlinlab.model.Transport
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.function.Consumer

class SwitchMonitor {
    suspend fun switch(
        transport: Transport,
        jobs: List<Job>,
        completeSection: Consumer<Pair<Transport, Pair<String, String>>>
    ) = coroutineScope {
        val distance = distanceToSwitch(transport)

        do {
            delay(transport.timeStep / 3)
        } while (distance >= transport.getPosition())

        jobs.forEach { it.cancel() }

        launch { transport.motionLoop() }

        val sectionLeft = transport.section()
        transport.switchSection(
            section = Pair("${Line.getLine(sectionLeft.first)}:${sectionLeft.second}", "${sectionLeft.second}|")
        )
        completeSection.accept(Pair(transport.also { it.addSection() }, sectionLeft))
    }

    companion object {
        fun replaceSwitch(station: String): String = station.replace("|", "")
        fun distanceToSwitch(transport: Transport): Double =
            if (transport.section().first.contains("|")) {
                transport.line.switchTrackDistance
            } else {
                transport.getJourneyTime().third - transport.line.switchTrackDistance
            }
    }
}
