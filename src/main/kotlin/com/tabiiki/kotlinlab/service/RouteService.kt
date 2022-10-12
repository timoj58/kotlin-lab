package com.tabiiki.kotlinlab.service

import com.tabiiki.kotlinlab.factory.RouteFactory
import com.tabiiki.kotlinlab.repo.StationRepo
import org.springframework.stereotype.Service

interface RouteService {
    fun generate(): Pair<String, String>
    fun routeFactory(): RouteFactory
}

@Service
class RouteServiceImpl(
    val stationRepo: StationRepo,
    val routeFactory: RouteFactory
): RouteService {
    override fun generate(): Pair<String, String> {
        val stations = stationRepo.get()

        return Pair(stations.random().id, stations.random().id)
    }

    override fun routeFactory(): RouteFactory = routeFactory

}