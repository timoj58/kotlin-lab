package com.tabiiki.kotlinlab.util

import com.tabiiki.kotlinlab.configuration.LinesConfig
import com.tabiiki.kotlinlab.configuration.StationsConfig
import com.tabiiki.kotlinlab.configuration.TransportConfig
import com.tabiiki.kotlinlab.configuration.TransportersConfig
import com.tabiiki.kotlinlab.configuration.adapter.LinesAdapter
import com.tabiiki.kotlinlab.configuration.adapter.TransportersAdapter
import com.tabiiki.kotlinlab.factory.InterchangeFactory
import com.tabiiki.kotlinlab.factory.LineFactory
import com.tabiiki.kotlinlab.factory.StationFactory

class InterchangeFactoryBuilder {
    fun build(): InterchangeFactory {
        val linesAdapter = LinesAdapter(
            listOf(
                "src/main/resources/network/dockland/dlr.yml",
                "src/main/resources/network/underground/jubilee.yml",
                "src/main/resources/network/underground/district.yml",
                "src/main/resources/network/underground/circle.yml",
                "src/main/resources/network/underground/city.yml",
                "src/main/resources/network/underground/bakerloo.yml",
                "src/main/resources/network/underground/northern.yml",
                "src/main/resources/network/underground/piccadilly.yml",
                "src/main/resources/network/underground/victoria.yml",
                "src/main/resources/network/underground/central.yml"
            ),
            listOf(
                "src/main/resources/network/overground/gospel-oak.yml",
                "src/main/resources/network/overground/highbury-islington.yml",
                "src/main/resources/network/overground/london-euston.yml",
                "src/main/resources/network/overground/romford.yml",
                "src/main/resources/network/overground/london-liverpool-st.yml",
                "src/main/resources/network/overground/stratford.yml",
                "src/main/resources/network/overground/elizabeth.yml"
            ),
            listOf(
                "src/main/resources/network/tram/tram.yml"
            ),
            listOf(
                "src/main/resources/network/cable/cable.yml"
            ),
            listOf(
                "src/main/resources/network/river/river.yml",
            ),
            listOf("src/main/resources/network/dockland/dlr.yml")
        )
        val linesConfig = LinesConfig(linesAdapter)
        val transportersAdapter = TransportersAdapter(
            listOf(
                TransportConfig(
                    transportId = 1,
                    capacity = 1000,
                    weight = 1500,
                    topSpeed = 20,
                    power = 2300
                ),
                TransportConfig(
                    transportId = 2,
                    capacity = 1000,
                    weight = 1500,
                    topSpeed = 20,
                    power = 2300
                ),
                TransportConfig(
                    transportId = 3,
                    capacity = 1000,
                    weight = 1500,
                    topSpeed = 20,
                    power = 2300
                ),
                TransportConfig(
                    transportId = 4,
                    capacity = 1000,
                    weight = 1500,
                    topSpeed = 20,
                    power = 2300
                ),
                TransportConfig(
                    transportId = 5,
                    capacity = 1000,
                    weight = 1500,
                    topSpeed = 20,
                    power = 2300
                ),
                TransportConfig(
                    transportId = 6,
                    capacity = 1000,
                    weight = 1500,
                    topSpeed = 20,
                    power = 2300
                ),
                TransportConfig(
                    transportId = 7,
                    capacity = 1000,
                    weight = 1500,
                    topSpeed = 20,
                    power = 2300
                ),
            )
        )
        val transportConfig = TransportersConfig(transportersAdapter)
        val stationsConfig = StationsConfig("src/main/resources/network/stations.csv")
        val stationFactory = StationFactory(stationsConfig)

        val lineFactory = LineFactory(10, transportConfig, linesConfig)
        return InterchangeFactory(lineFactory, stationFactory)
    }
}