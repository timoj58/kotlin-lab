package com.tabiiki.kotlinlab.service

import com.tabiiki.kotlinlab.factory.StationFactory
import com.tabiiki.kotlinlab.model.Station
import org.springframework.stereotype.Service


interface StationsService {
    fun getNextStationOnLine(lineStations: List<String>, linePosition: Pair<String, String>): Station
    fun get(): List<Station>
}

@Service
class StationsServiceImpl(
    stationFactory: StationFactory
) : StationsService {
    private val stations = stationFactory.get().map { stationFactory.get(it) }

    override fun getNextStationOnLine(lineStations: List<String>,linePosition: Pair<String, String>): Station {
        val fromStationIdx = lineStations.indexOf(linePosition.first)
        val toStationIdx = lineStations.indexOf(linePosition.second)
        val direction = fromStationIdx - toStationIdx

        return if (direction > 0) if (toStationIdx > 0) getStationById(lineStations[toStationIdx - 1]) else getStationById(lineStations[1]) else
            if (toStationIdx < lineStations.size - 1) getStationById(lineStations[toStationIdx + 1]) else getStationById(lineStations.reversed()[1])

    }

    override fun get(): List<Station> = stations.toList()

    private fun getStationById(id: String): Station = stations.find { it.id == id }!!

}