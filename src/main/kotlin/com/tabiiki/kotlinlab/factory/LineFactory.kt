package com.tabiiki.kotlinlab.factory

import com.tabiiki.kotlinlab.configuration.LinesConfig
import com.tabiiki.kotlinlab.configuration.TransportersConfig
import com.tabiiki.kotlinlab.model.Line
import com.tabiiki.kotlinlab.model.LineNetwork
import com.tabiiki.kotlinlab.model.TransportMessage
import com.tabiiki.kotlinlab.monitor.SwitchMonitor
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
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
        if (timeStep < 1) throw ConfigurationException("timestep is too small, minimum 7 ms")
    }

    private val lines =
        linesConfig.lines.map {
            Line(
                timeStep = timeStep,
                config = it,
                transportConfig = transportConfig.get()
            )
        }

    init {
        lines.groupBy { it.name }.values.forEach { line -> lineNetworks[line.first().name] = LineNetwork(line) }
    }

    fun get(id: String): Line = lines.find { it.id == id } ?: throw NoSuchElementException("Line missing")
    fun get(): List<String> = lines.map { it.id }
    fun getNetwork(id: String): LineNetwork? = lineNetworks[id]

    fun getStationLines(stationId: String): List<Line> = get().map { get(it) }.filter {
        it.stations.contains(stationId)
    }

    fun isSwitchStation(line: String, station: String): Boolean {
        // TODO exposing the **, *** terminals for RIVER side effects elsewhere? or part of the other bug so safe to ignore.
        val network = getNetwork(line) ?: return false
        return network.getNodes().any {
            (it.station == SwitchMonitor.replaceSwitch(station)) &&
                it.linked.any { n -> n.contains("*") } && it.linked.size > 1
        }
    }

    fun isJunction(line: String, station: String): Boolean {
        val network = getNetwork(line) ?: return false
        return network.getNodes().first { it.station == station }.linked.size > 2
    }

    fun isSwitchSection(lineId: String, section: Pair<String, String>): Pair<Boolean, Boolean> =
        Pair(isSwitchStation(lineId, section.first), isSwitchStation(lineId, section.second))

    suspend fun tracking(channel: Channel<TransportMessage>): Unit = coroutineScope {
        lines.forEach {
            it.transporters.forEach {
                    transport ->
                launch { transport.track(channel) }
            }
        }
    }
}
