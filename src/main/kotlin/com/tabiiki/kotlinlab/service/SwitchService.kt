package com.tabiiki.kotlinlab.service

import com.tabiiki.kotlinlab.factory.LineFactory
import com.tabiiki.kotlinlab.model.Line
import com.tabiiki.kotlinlab.model.Transport
import com.tabiiki.kotlinlab.monitor.SwitchMonitor
import kotlinx.coroutines.Job
import org.springframework.stereotype.Service
import java.util.function.Consumer

@Service
class SwitchService(
    private val lineFactory: LineFactory
) {
    private val switchMonitor = SwitchMonitor()

    fun isSwitchSection(transport: Transport): Boolean {
        val section = getSection(transport.section())
        val isPossibleSwitch = lineFactory.isSwitchSection(transport.line.name, section)
        if (!isPossibleSwitch.first && !isPossibleSwitch.second) return false

        val firstStation = getFirstStation(transport)
        val lastStation = getLastStation(transport)

        return firstStation == SwitchMonitor.replaceSwitch(section.first) && isPossibleSwitch.first ||
            lastStation == SwitchMonitor.replaceSwitch(section.second) && isPossibleSwitch.second
    }

    fun isSwitchPlatform(transport: Transport, section: Pair<String, String>, destination: Boolean = false): Boolean {
        val switchSection = lineFactory.isSwitchStation(
            transport.line.name,
            if (destination) section.second else getSection(section).first
        )
        if (!switchSection) return false

        val firstStation = transport.line.stations.first()
        val lastStation = transport.line.stations.last()

        return listOf(firstStation, lastStation).contains(
            if (destination) {
                section.second
            } else {
                SwitchMonitor.replaceSwitch(Line.getStation(section.first))
            }
        )
    }

    suspend fun switch(
        transport: Transport,
        jobs: List<Job>,
        completeSection: Consumer<Pair<Transport, Pair<String, String>>>
    ) = switchMonitor.switch(transport, jobs, completeSection)

    companion object {
        private fun getSection(section: Pair<String, String>): Pair<String, String> =
            Pair(Line.getStation(section.first), section.second)

        private fun getFirstStation(transport: Transport): String =
            if (transport.lineDirection() == LineDirection.NEGATIVE) transport.line.stations.last() else transport.line.stations.first()

        private fun getLastStation(transport: Transport): String =
            if (transport.lineDirection() == LineDirection.NEGATIVE) transport.line.stations.first() else transport.line.stations.last()
    }
}
