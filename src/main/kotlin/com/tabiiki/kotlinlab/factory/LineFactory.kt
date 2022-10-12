package com.tabiiki.kotlinlab.factory

import com.tabiiki.kotlinlab.configuration.LinesConfig
import com.tabiiki.kotlinlab.configuration.TransportersConfig
import com.tabiiki.kotlinlab.model.Line
import com.tabiiki.kotlinlab.model.LineNetwork
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import javax.naming.ConfigurationException

@Component
class LineFactory(
    @Value("\${network.time-step}") val timeStep: Long,
    transportConfig: TransportersConfig,
    linesConfig: LinesConfig
) {
    private val lineNetworks: MutableMap<String, LineNetwork> = mutableMapOf()

    init {
        if (timeStep < 5) throw ConfigurationException("timestep is too small, minimum 10 ms")
    }

    private val lines =
        linesConfig.lines.map { Line(timeStep, it, transportConfig.get(), linesConfig.defaultLineCapacity) }

    init {
        lines.groupBy { it.name }.values.forEach { line -> lineNetworks[line.first().name] = LineNetwork(line) }
    }

    fun get(id: String): Line = lines.find { it.id == id } ?: throw NoSuchElementException("Line missing")
    fun get(): List<String> = lines.map { it.id }
    fun getNetwork(id: String): LineNetwork? = lineNetworks[id]

    fun isSwitchStation(lineId: String, station: String): Boolean {
        val network = getNetwork(lineId) ?: return false
        return network.getNodes().any {
            (it.station == station.replace("|", ""))
                    && it.linked.contains("*")
                    && it.linked.size > 1
        }

    }

    fun isSwitchSection(lineId: String, section: Pair<String, String>): Pair<Boolean, Boolean> =
        Pair(isSwitchStation(lineId, section.first), isSwitchStation(lineId, section.second))
}