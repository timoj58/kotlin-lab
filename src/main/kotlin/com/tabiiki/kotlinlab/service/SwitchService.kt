package com.tabiiki.kotlinlab.service

import com.tabiiki.kotlinlab.factory.LineFactory
import com.tabiiki.kotlinlab.model.Transport
import com.tabiiki.kotlinlab.monitor.SwitchMonitor
import org.springframework.stereotype.Service
import java.util.function.Consumer

interface SwitchService {
    fun isSwitchSection(transport: Transport): Boolean
    fun isSwitchPlatform(transport: Transport, section: Pair<String, String>, destination: Boolean = false): Boolean
    suspend fun switch(transport: Transport, completeSection: Consumer<Pair<Transport, Pair<String, String>>>)
}

@Service
class SwitchServiceImpl(
    private val lineFactory: LineFactory
) : SwitchService {

    private val switchMonitor = SwitchMonitor()

    override fun isSwitchSection(transport: Transport): Boolean {
        val section = getSection(transport.section())
        val isPossibleSwitch = lineFactory.isSwitchSection(transport.line.name, section)
        if (!isPossibleSwitch.first && !isPossibleSwitch.second) return false

        val firstStation = transport.line.stations.first()
        val lastStation = transport.line.stations.last()

        return firstStation == section.first.replace("|", "") && isPossibleSwitch.first
                || lastStation == section.second.replace("|", "") && isPossibleSwitch.second
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
            else section.first.substringAfter(":").replace("|", "")
        )
    }

    override suspend fun switch(
        transport: Transport,
        completeSection: Consumer<Pair<Transport, Pair<String, String>>>
    ) = switchMonitor.switch(transport, completeSection)

    companion object {
        private fun getSection(section: Pair<String, String>): Pair<String, String> =
            Pair(section.first.substringAfter(":"), section.second)
    }

}