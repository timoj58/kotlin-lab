package com.tabiiki.kotlinlab.service

import com.tabiiki.kotlinlab.factory.AvailableRoute
import com.tabiiki.kotlinlab.factory.RouteFactory
import com.tabiiki.kotlinlab.model.Station
import com.tabiiki.kotlinlab.repo.StationRepo
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.springframework.stereotype.Service

data class RouteEnquiry(
    val route: Pair<String, String>,
    val channel: Channel<AvailableRoute>,
    val depth: Int = 2
)

@Service
class RouteService(
    private val stationRepo: StationRepo,
    private val routeFactory: RouteFactory
) {
    private val channel: Channel<RouteEnquiry> = Channel()
    fun generate(): Pair<Pair<String, String>, Channel<RouteEnquiry>> {
        val stations = stationRepo.get()
        val from = generateStation(stations)
        return Pair(
            Pair(
                from,
                generateStation(stations, from)
            ),
            channel
        )
    }

    suspend fun listen() = coroutineScope {
        do {
            val enquiry = channel.receive()
            launch { routeFactory.generateAvailableRoutes(enquiry) }
        } while (true)
    }

    private fun generateStation(stations: List<Station>, from: String? = null): String {
        var station: String? = null
        while (station == null) {
            val random = stations.random().id
            if (routeFactory.isSelectableStation(random) && from.orEmpty() != random) station = random
        }

        return station
    }
}
