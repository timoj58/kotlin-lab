package com.tabiiki.kotlinlab.service

import com.tabiiki.kotlinlab.model.Transport
import com.tabiiki.kotlinlab.repo.StationRepo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.springframework.stereotype.Service

interface LineConductor {
    suspend fun hold(transport: Transport, delay: Int, lineStations: List<String>)
    suspend fun depart(transport: Transport, lineStations: List<String>)
}

@Service
class LineConductorImpl(private val stationRepo: StationRepo) : LineConductor {
    override suspend fun hold(transport: Transport, delay: Int, lineStations: List<String>): Unit = coroutineScope {
        if (transport.holdCounter > delay) launch(Dispatchers.Default) { depart(transport, lineStations) }
    }

    override suspend fun depart(transport: Transport, lineStations: List<String>) {
        transport.depart(
            stationRepo.get(transport.linePosition.first),
            stationRepo.get(transport.linePosition.second),
            //TODO this goes backwards.....TEST IT on circle
            stationRepo.getNextStationOnLine(
                lineStations = lineStations, linePosition = transport.linePosition
            )
        )
    }
}
