package com.tabiiki.kotlinlab.service

import com.tabiiki.kotlinlab.factory.LineFactory
import com.tabiiki.kotlinlab.model.Line
import com.tabiiki.kotlinlab.model.Transport
import com.tabiiki.kotlinlab.monitor.SwitchMonitor
import com.tabiiki.kotlinlab.repo.LineDirection
import kotlinx.coroutines.Job
import org.springframework.stereotype.Service
import java.util.function.Consumer

interface SwitchService {
    fun isStationTerminal(station: String): Boolean
    fun isSwitchSectionByTerminal(transport: Transport): Pair<Boolean, Boolean>
    fun doesFirstStationInSectionContainTerminal(transport: Transport): Boolean
    fun isSwitchSection(transport: Transport): Boolean
    fun isSwitchPlatform(transport: Transport, section: Pair<String, String>, destination: Boolean = false): Boolean
    suspend fun switch(
        transport: Transport,
        jobs: List<Job>,
        completeSection: Consumer<Pair<Transport, Pair<String, String>>>
    )
}

@Service
class SwitchServiceImpl(
    private val lineFactory: LineFactory
) : SwitchService {

    private val switchMonitor = SwitchMonitor()
    override fun isStationTerminal(station: String): Boolean {
        val lineName = station.substringBefore(":")
        val stationCode = station.substringAfter(":")
        val isPossibleSwitch = lineFactory.isSwitchSection(lineName, Pair(stationCode, stationCode))

        return isPossibleSwitch.first
    }

    override fun isSwitchSectionByTerminal(transport: Transport): Pair<Boolean, Boolean> {
        val section = getSection(transport.section())
        val isPossibleSwitch = lineFactory.isSwitchSection(transport.line.name, section)
        if (!isPossibleSwitch.first && !isPossibleSwitch.second) return Pair(false, false)

        val firstStation = getFirstStation(transport)
        val lastStation = getLastStation(transport)

        return Pair(
            firstStation == SwitchMonitor.replaceSwitch(section.first) && isPossibleSwitch.first,
            lastStation == SwitchMonitor.replaceSwitch(section.second) && isPossibleSwitch.second
        )
    }

    override fun doesFirstStationInSectionContainTerminal(transport: Transport): Boolean {
        val section = getSection(transport.section())
        val isPossibleSwitch = lineFactory.isSwitchSection(transport.line.name, section)

        return isPossibleSwitch.first
    }

    override fun isSwitchSection(transport: Transport): Boolean {

        val section = getSection(transport.section())
        val isPossibleSwitch = lineFactory.isSwitchSection(transport.line.name, section)
        if (!isPossibleSwitch.first && !isPossibleSwitch.second) return false

        val firstStation = getFirstStation(transport)
        val lastStation = getLastStation(transport)

        return firstStation == SwitchMonitor.replaceSwitch(section.first) && isPossibleSwitch.first
                || lastStation == SwitchMonitor.replaceSwitch(section.second) && isPossibleSwitch.second
    }

    override fun isSwitchPlatform(transport: Transport, section: Pair<String, String>, destination: Boolean): Boolean {
        val switchSection = lineFactory.isSwitchStation(
            transport.line.name,
            if (destination) section.second else getSection(section).first
        )
        if (!switchSection) return false

        val firstStation = transport.line.stations.first()
        val lastStation = transport.line.stations.last()

        return listOf(firstStation, lastStation).contains(
            if (destination) section.second
            else SwitchMonitor.replaceSwitch(Line.getStation(section.first))
        )
    }

    override suspend fun switch(
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