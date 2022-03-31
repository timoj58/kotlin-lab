package com.tabiiki.kotlinlab.repo

import com.tabiiki.kotlinlab.factory.StationFactory
import com.tabiiki.kotlinlab.model.Station
import org.springframework.stereotype.Repository
import kotlin.math.abs


interface StationRepo {
    fun getNextStationOnLine(lineStations: List<String>, linePosition: Pair<String, String>): Station
    fun get(): List<Station>
    fun get(id: String): Station
}

@Repository
class StationRepoImpl(
    stationFactory: StationFactory
) : StationRepo {
    private val stations = stationFactory.get().map { stationFactory.get(it) }

   override fun getNextStationOnLine(lineStations: List<String>, linePosition: Pair<String, String>): Station {
        var fromStationIdx = lineStations.indexOf(linePosition.first)
        var toStationIdx = lineStations.indexOf(linePosition.second)
        if(abs(fromStationIdx - toStationIdx) > 1)
           if(fromStationIdx > toStationIdx)
               toStationIdx = lineStations.lastIndexOf(linePosition.second)
           else fromStationIdx = lineStations.lastIndexOf(linePosition.second)

        val direction = fromStationIdx - toStationIdx

        return if (direction >= 0) if (toStationIdx > 0) get(lineStations[toStationIdx - 1]) else get(lineStations[1]) else
            if (toStationIdx < lineStations.size - 1) get(lineStations[toStationIdx + 1]) else get(lineStations.reversed()[1])

    }

    override fun get(): List<Station> = stations.toList()
    override fun get(id: String): Station = stations.first { it.id == id }

}