package com.tabiiki.kotlinlab.model

import com.tabiiki.kotlinlab.configuration.LineConfig
import com.tabiiki.kotlinlab.configuration.LineType
import com.tabiiki.kotlinlab.configuration.TransportConfig
import javax.naming.ConfigurationException
import kotlin.math.floor

data class Line(
    val timeStep: Long,
    private val config: LineConfig,
    private val transportConfig: List<TransportConfig>,
) {
    val id = config.id
    val name = config.name
    val stations = config.stations
    val transporters =
        generateSequence {
            transportConfig.map {
                Transport(
                    config = it,
                    line = this,
                    timeStep = timeStep
                )
            }.first { it.transportId == config.transportId }
        }.take(
            config.lineCapacity
        ).toList()
    private val depots = config.depots

    init {
        val perDepot = if (depots.isEmpty()) 0.0 else transporters.size / depots.size.toDouble()
        if (perDepot != 0.0 && transporters.size % floor(perDepot) != 0.0) throw ConfigurationException("transporters must be divisible by depots")
        var startIndex = 0
        val multiDepots = getMultiDepots()
        depots.distinct().forEach { depot ->
            transporterSlice(startIndex, perDepot.toInt(), depot) { d -> this.nextStationFromDepot(d) }
            startIndex += perDepot.toInt()
        }

        multiDepots.forEach { depot ->
            transporterSlice(startIndex, perDepot.toInt(), depot) { d -> this.nextStationFromMultiDepot(d) }
            startIndex += perDepot.toInt()
        }
    }

    fun getType(): LineType = config.type!!

    private fun getMultiDepots(): List<String> {
        var duplicateCount = mutableMapOf<String, Int>()

        depots.forEach { depot ->
            if (duplicateCount.containsKey(depot)) duplicateCount[depot] = duplicateCount[depot]!! + 1
            else duplicateCount[depot] = 1
        }

        return duplicateCount.filter { it.value > 1 }.keys.toList()
    }

    private fun transporterSlice(startIndex: Int, perDepot: Int, depot: String, nextStation: (String) -> String) {
        if (perDepot == 0) transporters.first().addSection(Pair("$name:$depot", nextStation(depot)))
        else transporters.slice(startIndex until startIndex + perDepot)
            .forEach { transport -> transport.addSection(Pair("$name:$depot", nextStation(depot))) }
    }

    private fun nextStationFromDepot(currentStation: String): String =
        if (stations.last() == currentStation) stations.reversed()[1] else stations[stations.indexOf(currentStation) + 1]

    private fun nextStationFromMultiDepot(currentStation: String): String =
        stations[stations.indexOfFirst { it == currentStation } + 1]

    companion object {
        fun getStation(details: String): String = details.substringAfter(":")
        fun getLine(details: String): String = details.substringBefore(":")
    }

}