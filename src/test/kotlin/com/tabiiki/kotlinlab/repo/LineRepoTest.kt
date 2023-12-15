package com.tabiiki.kotlinlab.repo

import com.tabiiki.kotlinlab.configuration.LinesConfig
import com.tabiiki.kotlinlab.configuration.StationsConfig
import com.tabiiki.kotlinlab.configuration.TransportConfig
import com.tabiiki.kotlinlab.configuration.TransportersConfig
import com.tabiiki.kotlinlab.configuration.adapter.LinesAdapter
import com.tabiiki.kotlinlab.configuration.adapter.TransportersAdapter
import com.tabiiki.kotlinlab.factory.LineFactory
import com.tabiiki.kotlinlab.factory.StationFactory
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class LineRepoTest {
    // TODO issue relates to missing stations - signals are turning off the wrong light, ie not contiguous
    private val stationsConfig = StationsConfig("src/main/resources/network/stations.csv")
    private val stationFactory = StationFactory(stationsConfig)
    private val stationRepo = StationRepo(stationFactory)
    // private val lineRepo = LineRepo(stationRepo)

    private val linesAdapter = LinesAdapter(
        listOf("src/main/resources/network/river/river.yml"),
        listOf(),
        listOf(),
        listOf(),
        listOf(),
        listOf()
    )
    private val linesConfig = LinesConfig(linesAdapter)
    private val transportersAdapter = TransportersAdapter(
        listOf(
            TransportConfig(
                transportId = 1,
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
            )
        )
    )
    private val transportConfig = TransportersConfig(transportersAdapter)

    private val lineFactory = LineFactory(10, transportConfig, linesConfig)

    @BeforeEach
    fun init() {
        lineFactory.get().map { lineFactory.get(it) }.groupBy { it.name }.forEach {
            //     lineRepo.addLineDetails(it.key, it.value)
        }
    }

    @Test
    fun `get lines stations`() {
        //       val sections = lineRepo.getPreviousSections(platformKey = Pair("River:POSITIVE", "River:671"))
        //    println("$sections")
        //     println(lineRepo.getPreviousSections(platformKey = Pair("River:POSITIVE", "River:673")))
    }
}
