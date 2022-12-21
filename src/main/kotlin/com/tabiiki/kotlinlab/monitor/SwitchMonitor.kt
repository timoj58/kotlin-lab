package com.tabiiki.kotlinlab.monitor

import com.tabiiki.kotlinlab.model.Line
import com.tabiiki.kotlinlab.model.Transport
import kotlinx.coroutines.delay
import java.util.function.Consumer

class SwitchMonitor {
    suspend fun switch(
        transport: Transport,
        completeSection: Consumer<Pair<Transport, Pair<String, String>>>
    ) {
        val distance = distanceToSwitch(transport)

        do {
            delay(transport.timeStep)
        } while (distance >= transport.getPosition())

        val sectionLeft = transport.section()
        transport.switchSection(
            section = Pair("${Line.getLine(sectionLeft.first)}:${sectionLeft.second}", "${sectionLeft.second}|")
        )
        println("switching ${transport.id} ${transport.section()}")
        completeSection.accept(Pair(transport.also { it.addSection() }, sectionLeft))
    }

    companion object {
        fun replaceSwitch(station: String): String = station.replace("|", "")
        fun distanceToSwitch(transport: Transport): Double =
            if (transport.section().first.contains("|")) 100.0 else transport.getJourneyTime().third - 100.0
    }
}