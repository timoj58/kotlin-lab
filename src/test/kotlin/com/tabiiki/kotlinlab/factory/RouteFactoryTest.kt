package com.tabiiki.kotlinlab.factory

import com.tabiiki.kotlinlab.configuration.LinesConfig
import com.tabiiki.kotlinlab.configuration.TransportConfig
import com.tabiiki.kotlinlab.configuration.TransportersConfig
import com.tabiiki.kotlinlab.configuration.adapter.LinesAdapter
import com.tabiiki.kotlinlab.configuration.adapter.TransportersAdapter
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test

class RouteFactoryTest {
    private val linesAdapter = LinesAdapter(listOf(
        "src/main/resources/network/dockland/dlr.yml",
        "src/main/resources/network/underground/jubilee.yml",
        "src/main/resources/network/underground/district.yml",
        ))
    private val linesConfig = LinesConfig(linesAdapter)
    private val transportersAdapter = TransportersAdapter(
        listOf(
            TransportConfig(
                transportId = 3,
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
        )
    )
    private val transportConfig = TransportersConfig(transportersAdapter)

    private val lineFactory = LineFactory(10, transportConfig, linesConfig)
    private val routeFactory = RouteFactory(lineFactory)

    @Test
    fun `interchange test ` () {
        Assertions.assertThat(routeFactory.getInterchanges()).containsAll(
            listOf("95","94","615","528","629")
        )
    }

    @Test
    fun `calculate routes from Stratford to Canary Wharf `() {
        val routes = routeFactory.getAvailableRoutes(Pair("528","94"))

        Assertions.assertThat(routes).contains(
            listOf(Pair("Jubilee:528","Jubilee:615"),Pair("Jubilee:615","Jubilee:95"),Pair("Jubilee:95","Jubilee:396"), Pair("Jubilee:396","Jubilee:94")),
            listOf(Pair("DLR:528","DLR:438"),Pair("DLR:438","DLR:67"),Pair("DLR:67","DLR:163"),Pair("DLR:163","DLR:330"),Pair("DLR:330","DLR:12")
                ,Pair("DLR:12","DLR:435"),Pair("DLR:435","DLR:619"), Pair("DLR:619","DLR:94")),
        )
    }

    @Test
    fun `calculate routes for Beckton to Canary Wharf`() {
        val routes = routeFactory.getAvailableRoutes(Pair("41","94"))

        Assertions.assertThat(routes).contains(
            listOf(Pair("DLR:41","DLR:219"), Pair("DLR:219","DLR:153"), Pair("DLR:153","DLR:42"), Pair("DLR:42","DLR:466"),
                Pair("DLR:466","DLR:437"),Pair("DLR:437","DLR:151"),Pair("DLR:151","DLR:468"),Pair("DLR:468","DLR:95"),
                Pair("Jubilee:95","Jubilee:396"),Pair("Jubilee:396","Jubilee:94")),
        )

        Assertions.assertThat(routes).contains(
            listOf(Pair("DLR:41","DLR:219"), Pair("DLR:219","DLR:153"), Pair("DLR:153","DLR:42"), Pair("DLR:42","DLR:466"),
                Pair("DLR:466","DLR:437"),Pair("DLR:437","DLR:151"),Pair("DLR:151","DLR:468"),Pair("DLR:468","DLR:95"),
                Pair("Jubilee:95","Jubilee:615"),Pair("Jubilee:615","Jubilee:528"),
                Pair("DLR:528","DLR:438"),Pair("DLR:438","DLR:67"),Pair("DLR:67","DLR:163"),Pair("DLR:163","DLR:330"),
                Pair("DLR:330","DLR:12"),Pair("DLR:12","DLR:435"),Pair("DLR:435","DLR:619"),Pair("DLR:619","DLR:94"),
            ),
        )
    }

}