package com.tabiiki.kotlinlab.factory

import com.tabiiki.kotlinlab.configuration.LinesConfig
import com.tabiiki.kotlinlab.configuration.StationsConfig
import com.tabiiki.kotlinlab.model.Station
import org.springframework.stereotype.Repository

@Repository
class StationFactory(
    stationsConfig: StationsConfig,
    linesConfig: LinesConfig
) {
    private val stations = stationsConfig.stations.map { config ->
        Station(
            config,
            linesConfig.lines.filter { it.stations.contains(config.id) }.map { it.id }
        )
    }

    fun get(id: String): Station = stations.find { it.id == id } ?: throw NoSuchElementException("Station missing")
    fun get(): List<String> = stations.map { it.id }
}