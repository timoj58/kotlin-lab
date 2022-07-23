package com.tabiiki.kotlinlab.service

import com.tabiiki.kotlinlab.factory.LineFactory
import com.tabiiki.kotlinlab.model.Transport
import kotlinx.coroutines.delay
import org.springframework.stereotype.Service
import java.util.function.Consumer

interface SwitchService {
    fun isSwitchSection(transport: Transport): Boolean
    fun isSwitchPlatform(transport: Transport, section: Pair<String, String>, destination: Boolean = false): Boolean
    suspend fun switch(transport: Transport, completeSection: Consumer<Pair<Transport, Pair<String, String>>>)
    fun distanceToSwitch(transport: Transport): Double
}

@Service
class SwitchServiceImpl(
    private val lineFactory: LineFactory
) : SwitchService {
    override fun isSwitchSection(transport: Transport): Boolean {
        val section = getSection(transport.section())
        val isPossibleSwitch = lineFactory.isSwitchSection(transport.line.name, section)
        if (!isPossibleSwitch.first && !isPossibleSwitch.second) return false

        val firstStation = transport.line.stations.first()
        val lastStation = transport.line.stations.last()

        return firstStation == section.first && isPossibleSwitch.first || lastStation == section.second && isPossibleSwitch.second
    }

    override fun isSwitchPlatform(transport: Transport, section: Pair<String, String>, destination: Boolean): Boolean {
        val switchSection = lineFactory.isSwitchStation(
            transport.line.name,
            if (destination) section.second else getSection(section).first
        )
        if (!switchSection) return false

        val firstStation = transport.line.stations.first()
        val lastStation = transport.line.stations.last()

        val station = if (destination) section.second else section.first.substringAfter(":")

        return listOf(firstStation, lastStation).contains(station)
    }

    override suspend fun switch(
        transport: Transport,
        completeSection: Consumer<Pair<Transport, Pair<String, String>>>
    ) {
        val distance = distanceToSwitch(transport)

        do {
            delay(transport.timeStep)
        } while (distance >= transport.getPosition())

        val sectionLeft = transport.section()
        if (transport.actualSection == null) {
            transport.actualSection =
                Pair("${sectionLeft.first.substringBefore(":")}:${sectionLeft.second}", "${sectionLeft.second}|")
        }
        //println("switching ${transport.id} at $sectionLeft to ${transport.actualSection}")
        completeSection.accept(Pair(transport.also { it.addSection(it.actualSection!!) }, sectionLeft))
    }

    override fun distanceToSwitch(transport: Transport): Double =
        if (transport.section().first.contains("|")) 100.0 else transport.getJourneyTime().third - 100.0

    private fun getSection(section: Pair<String, String>): Pair<String, String> =
        Pair(section.first.substringAfter(":"), section.second)

}