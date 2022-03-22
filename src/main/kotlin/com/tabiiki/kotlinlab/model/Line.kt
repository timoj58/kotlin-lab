package com.tabiiki.kotlinlab.model

import com.tabiiki.kotlinlab.configuration.LineConfig
import com.tabiiki.kotlinlab.configuration.TransportConfig

data class Line(
    private val config: LineConfig,
    private val transportConfig: List<TransportConfig>
) {
    val id = config.id
    val name = config.name
    val stations = config.stations
    val transporters =
        generateSequence { transportConfig.map { Transport(it) }.first { it.transportId == config.transportId } }.take(
            config.transportCapacity
        ).toList()

    init {
        val divider = transporters.size / 2 + transporters.size % 2
        val startDepot = stations.first()
        val endDepot = stations.last()

        transporters.slice(0..divider)
            .forEach { transport -> transport.linePosition = Pair(startDepot, nextStationFromDepot(startDepot)) }

        transporters.slice(divider + 1 until transporters.size)
            .forEach { transport -> transport.linePosition = Pair(endDepot, nextStationFromDepot(endDepot)) }

    }

    private fun nextStationFromDepot(currentStation: String): String =
        if (stations.last() == currentStation) stations.reversed()[1] else stations[1]
}