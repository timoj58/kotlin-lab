package com.tabiiki.kotlinlab.repo

import com.tabiiki.kotlinlab.factory.StationFactory
import com.tabiiki.kotlinlab.model.Line
import com.tabiiki.kotlinlab.model.Station
import org.springframework.stereotype.Repository
import kotlin.math.abs


interface StationRepo {
    fun getNextStationOnLine(lineStations: List<String>, section: Pair<String, String>): Station
    fun getPreviousStationOnLine(lineStations: List<String>, section: Pair<String, String>): Station
    fun getPreviousStationsOnLine(lineStations: List<Line>, stationTo: String, direction: LineDirection): List<Station>
    fun get(): List<Station>
    fun get(id: String): Station
}

@Repository
class StationRepoImpl(
    stationFactory: StationFactory
) : StationRepo {
    private val stations = stationFactory.get().map { stationFactory.get(it) }

    override fun getNextStationOnLine(lineStations: List<String>, section: Pair<String, String>): Station {
        val config = getConfig(lineStations, section)
        val toStationIdx = config.second
        val direction = config.third

        return if (direction >= 0) if (toStationIdx > 0) get(lineStations[toStationIdx - 1]) else get(lineStations[1]) else
            if (toStationIdx < lineStations.size - 1) get(lineStations[toStationIdx + 1]) else get(lineStations.reversed()[1])
    }

    override fun getPreviousStationOnLine(lineStations: List<String>, section: Pair<String, String>): Station {
        val config = getConfig(lineStations, section)
        val fromStationIdx = config.first
        val direction = config.third

        return if (direction < 0) if (fromStationIdx > 0) get(lineStations[fromStationIdx - 1]) else get(lineStations[1]) else
            if (fromStationIdx < lineStations.size - 1) get(lineStations[fromStationIdx + 1]) else get(lineStations.reversed()[1])
    }

    override fun getPreviousStationsOnLine(
        lineStations: List<Line>,
        stationTo: String,
        direction: LineDirection
    ): List<Station> {
        val stations = mutableListOf<Station>()

        outer@ for (line in lineStations.filter { it.stations.size > 2 }) {
            val firstIdx = line.stations.indexOf(stationTo)
            val lastIdx = line.stations.lastIndexOf(stationTo)
            if (firstIdx == -1) continue@outer
            if (firstIdx == lastIdx) {
                stations.addAll(addStations(firstIdx, direction, line.stations))
            } else {
                //probably not called either. TODO
                if (!listOf(0, line.stations.size - 1).contains(lastIdx)
                ) continue@outer
                stations.addAll(addStations(firstIdx, direction, line.stations))
                stations.addAll(addStations(lastIdx, direction, line.stations))
            }
        }
        return stations
    }

    override fun get(): List<Station> = stations.toList()
    override fun get(id: String): Station =
        stations.firstOrNull { it.id == id } ?: throw Exception("missing details for $id")

    private fun getConfig(lineStations: List<String>, section: Pair<String, String>): Triple<Int, Int, Int> {
        var fromStationIdx = lineStations.indexOf(Line.getStation(section.first))
        var toStationIdx = lineStations.indexOf(section.second)
        if (abs(fromStationIdx - toStationIdx) > 1)
            if (fromStationIdx > toStationIdx)
                toStationIdx = lineStations.lastIndexOf(section.second)
            else fromStationIdx = lineStations.lastIndexOf(Line.getStation(section.first))
        val direction = fromStationIdx - toStationIdx

        return Triple(fromStationIdx, toStationIdx, direction)
    }

    private fun addStations(index: Int, direction: LineDirection, stations: List<String>): List<Station> {
        val result = mutableListOf<Station>()
        val max = stations.size - 1
        when (index) {
            0 -> result.add(get(stations[1]))
            max -> result.add(get(stations[stations.size - 2]))
            else ->
                when (direction) {
                    LineDirection.POSITIVE -> result.add(get(stations[index - 1]))
                    LineDirection.NEGATIVE -> result.add(get(stations[index + 1]))
                    //Terminals should always error, as they will be captured in 0 / max condition
                    LineDirection.TERMINAL -> throw RuntimeException("previous station for terminal")
                }
        }
        return result
    }

}