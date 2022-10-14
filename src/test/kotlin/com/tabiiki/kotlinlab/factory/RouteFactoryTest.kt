package com.tabiiki.kotlinlab.factory

import com.tabiiki.kotlinlab.configuration.LinesConfig
import com.tabiiki.kotlinlab.configuration.TransportConfig
import com.tabiiki.kotlinlab.configuration.TransportersConfig
import com.tabiiki.kotlinlab.configuration.adapter.LinesAdapter
import com.tabiiki.kotlinlab.configuration.adapter.TransportersAdapter
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

class RouteFactoryTest {
    private val linesAdapter = LinesAdapter(listOf(
        "src/main/resources/network/dockland/dlr.yml",
        "src/main/resources/network/underground/jubilee.yml",
        "src/main/resources/network/underground/district.yml",
        "src/main/resources/network/underground/circle.yml",
        "src/main/resources/network/underground/city.yml",
        "src/main/resources/network/underground/bakerloo.yml",
        "src/main/resources/network/underground/northern.yml",
        "src/main/resources/network/underground/piccadilly.yml",
        "src/main/resources/network/underground/victoria.yml",
        "src/main/resources/network/underground/hammersmith.yml",
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
    fun `calculate route including circle line Bayswater to Stratford `() {  //very slow but tests circle.
        routeFactory.getAvailableRoutes(Pair("37","528"))
        routeFactory.getAvailableRoutes(Pair("37","528"))
        val routes = routeFactory.getAvailableRoutes(Pair("37","528"))

        Assertions.assertThat(routes.size).isEqualTo(5614) //better tests at some point. will change as more routes added.

        //test if the circle interchange is present.
        Assertions.assertThat(routes).contains(
            listOf(Pair("Circle:37","Circle:418"),Pair("Circle:418","Circle:183"),Pair("Circle:183","Circle:24"),
                Pair("Jubilee:24","Jubilee:63"),Pair("Jubilee:63","Jubilee:235"),Pair("Jubilee:235","Jubilee:629"),
                Pair("Jubilee:629","Jubilee:598"), Pair("Jubilee:598","Jubilee:509"),Pair("Jubilee:509","Jubilee:345"),
                Pair("Jubilee:345","Jubilee:50"),Pair("Jubilee:50","Jubilee:93"),Pair("Jubilee:93","Jubilee:94"),
                Pair("Jubilee:94","Jubilee:396"),Pair("Jubilee:396","Jubilee:95"),Pair("Jubilee:95","Jubilee:615"),
                Pair("Jubilee:615","Jubilee:528")),
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