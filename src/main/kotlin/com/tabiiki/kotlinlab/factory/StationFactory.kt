package com.tabiiki.kotlinlab.factory

import com.tabiiki.kotlinlab.configuration.LinesConfig
import com.tabiiki.kotlinlab.configuration.StationsConfig
import com.tabiiki.kotlinlab.model.Station

class StationFactory(
    stationsConfig: StationsConfig,
    linesConfig: LinesConfig
) {
    private var stations = mutableListOf<Station>()

    init {
        stationsConfig.stations.forEach { station ->
            stations.add(Station(station,
                linesConfig.lines.filter { it.stations.contains(station.id) }
                    .map { it.id }
            ))
        }
    }

    fun get(id: String): Station? = stations.find { it.id == id }

}