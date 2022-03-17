package com.tabiiki.kotlinlab.factory

import com.tabiiki.kotlinlab.configuration.NetworkConfig
import com.tabiiki.kotlinlab.configuration.StationsConfig
import com.tabiiki.kotlinlab.model.Station

class StationFactory(
    stationsConfig: StationsConfig,
    networkConfig: NetworkConfig
) {
    private var stations =  mutableListOf<Station>()

    init {
          stationsConfig.stations.forEach{ station ->
              stations.add(Station(station,
                  networkConfig.lines.filter { it.stations.contains(station.id) }
                      .map { it.id }
              ))
          }
    }

    fun get(id: String): Station? = stations.find { it.id == id }

}