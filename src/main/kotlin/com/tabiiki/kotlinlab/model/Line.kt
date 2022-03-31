package com.tabiiki.kotlinlab.model

import com.tabiiki.kotlinlab.configuration.LineConfig
import com.tabiiki.kotlinlab.configuration.TransportConfig
import javax.naming.ConfigurationException

data class Line(
    val timeStep: Long,
    private val config: LineConfig,
    private val transportConfig: List<TransportConfig>
) {
    val id = config.id
    val name = config.name
    val stations = config.stations
    val holdDelay = config.holdDelay
    val transporters =
        generateSequence {
            transportConfig.map {
                Transport(
                    config = it,
                    lineId = id,
                    timeStep = timeStep
                )
            }.first { it.transportId == config.transportId }
        }.take(
            config.transportCapacity
        ).toList()
    private val depots = config.depots

    init {
        val perDepot = if (depots.isEmpty()) 0 else transporters.size / depots.size
        if (perDepot != 0 && transporters.size % perDepot != 0) throw ConfigurationException("transporters must be divisible by depots")
        var startIndex = 0
        val multiDepots = getMultiDepots()
        depots.distinct().forEach { depot ->
            transporterSlice(startIndex, perDepot, depot) { d -> this.nextStationFromDepot(d) }
            startIndex += perDepot
        }

        multiDepots.forEach { depot ->
            transporterSlice(startIndex, perDepot, depot) { d -> this.nextStationFromMultiDepot(d) }
            startIndex += perDepot
        }
    }

    private fun getMultiDepots(): List<String> {
        var duplicateCount = mutableMapOf<String, Int>()

        depots.forEach { depot ->
            if (duplicateCount.containsKey(depot)) duplicateCount[depot] = duplicateCount[depot]!! + 1
            else duplicateCount[depot] = 1
        }

        return duplicateCount.filter { it.value > 1 }.keys.toList()
    }

    private fun transporterSlice(startIndex: Int, perDepot: Int, depot: String, nextStation: (String) -> String) =
        transporters.slice(startIndex until startIndex + perDepot)
            .forEach { transport -> transport.linePosition = Pair(depot, nextStation(depot)) }


    private fun nextStationFromDepot(currentStation: String): String =
        if (stations.last() == currentStation) stations.reversed()[1] else stations[stations.indexOf(currentStation) + 1]

    private fun nextStationFromMultiDepot(currentStation: String): String =
        stations[stations.indexOfFirst { it == currentStation } + 1]

}