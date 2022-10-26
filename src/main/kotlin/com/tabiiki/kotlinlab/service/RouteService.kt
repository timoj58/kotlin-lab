package com.tabiiki.kotlinlab.service

import com.tabiiki.kotlinlab.factory.AvailableRoutes
import com.tabiiki.kotlinlab.factory.RouteFactory
import com.tabiiki.kotlinlab.model.Station
import com.tabiiki.kotlinlab.repo.StationRepo
import kotlinx.coroutines.channels.Channel
import org.springframework.stereotype.Service


data class RouteEnquiry(val route: Pair<String, String>, val channel: Channel<AvailableRoutes>)


interface RouteService {
    fun generate(): Pair<String, String>
    suspend fun listen()

    fun getChannel(): Channel<RouteEnquiry>
}

@Service
class RouteServiceImpl(
    private val stationRepo: StationRepo,
    private val routeFactory: RouteFactory
) : RouteService {
    override fun generate(): Pair<String, String> {
        val stations = stationRepo.get()
        val from = generateStation(stations)
        return Pair(from, generateStation(stations, from))
    }

    override suspend fun listen() {
        do {
            val enquiry = channel.receive()
            println("enquiry for: ${enquiry.route}")
            enquiry.channel.send(routeFactory.getAvailableRoutes(enquiry.route))
        } while (true)
    }

    override fun getChannel(): Channel<RouteEnquiry> = channel

    private fun generateStation(stations: List<Station>, from: String? = null): String {
        var station: String? = null
        while (station == null) {
            val random = stations.random().id
            if (routeFactory.isSelectableStation(random) && from.orEmpty() != random) station = random
        }

        return station
    }

    companion object {
        val channel: Channel<RouteEnquiry> = Channel()
    }
}