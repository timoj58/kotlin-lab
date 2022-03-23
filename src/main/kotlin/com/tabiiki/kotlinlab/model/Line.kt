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
    private val depots = config.depots

    init {
        val perDepot = if (depots.isEmpty()) 0 else transporters.size / depots.size
        var startIndex = 0
        depots.forEach { depot ->

            transporters.slice(startIndex until startIndex+perDepot)
                .forEach { transport -> transport.linePosition = Pair(depot, nextStationFromDepot(depot)) }

            startIndex+=perDepot
        }
    }

    private fun nextStationFromDepot(currentStation: String): String =
        if (stations.last() == currentStation) stations.reversed()[1] else stations[stations.indexOf(currentStation)+1]
}