package com.tabiiki.kotlinlab.configuration

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import java.io.File


data class StationConfig(val id: String,
                         val name: String = "",
                         val latitude: Double = 0.0,
                         val longitude: Double = 0.0,
                         val zones: String = "")

@Configuration
class StationsConfig(@Value("\${network.stations-csv}") stationsCsv: String) {

    private val loadStations = mutableListOf<StationConfig>()
    val stations
        get() = loadStations.toList()

    init {
        var counter = 0
        //headers: Station,OS X,OS Y,Latitude,Longitude,Zone,Postcode
        File(stationsCsv).forEachLine {
            val station = it.split(",")

            loadStations.add(
                StationConfig(
                    counter.toString(),
                    station[0],
                    station[3].toDouble(),
                    station[4].toDouble(),
                    station[5]
                )
            )
            counter++
        }
    }

}