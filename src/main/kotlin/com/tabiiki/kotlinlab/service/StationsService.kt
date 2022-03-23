package com.tabiiki.kotlinlab.service

import com.tabiiki.kotlinlab.factory.StationFactory
import com.tabiiki.kotlinlab.model.Station


interface StationsService {
    fun getNextStation(linePosition: Pair<String, String>): Station
    fun get(): List<Station>
}

class StationsServiceImpl(
    stationFactory: StationFactory
) : StationsService {

    private val stations = stationFactory.get().map { stationFactory.get(it) }

    override fun getNextStation(linePosition: Pair<String, String>): Station {
        val stationCodes = stations.map { it.id }
        val fromStationIdx = stationCodes.indexOf(linePosition.first)
        val toStationIdx = stationCodes.indexOf(linePosition.second)
        val direction = fromStationIdx - toStationIdx

        return if (direction > 0) if (toStationIdx > 0) stations[toStationIdx - 1] else stations[1] else
            if (toStationIdx < stations.size - 1) stations[toStationIdx + 1] else stations.reversed()[1]

    }

    override fun get(): List<Station> = stations.toList()

}