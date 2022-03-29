package com.tabiiki.kotlinlab.service

import com.tabiiki.kotlinlab.model.Transport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.springframework.stereotype.Service

interface LineConductor {
    suspend fun hold(transport: Transport, delay: Int, lineStations: List<String>)
    suspend fun depart(transport: Transport, lineStations: List<String>)
}

@Service
class LineConductorImpl(private val stationsService: StationsService) : LineConductor {
    override suspend fun hold(transport: Transport, delay: Int, lineStations: List<String>): Unit = coroutineScope {
        if (transport.holdCounter > delay) launch(Dispatchers.Default) { depart(transport, lineStations) }
    }

    override suspend fun depart(transport: Transport, lineStations: List<String>) {
        transport.depart(
            stationsService.get().first { it.id == transport.linePosition.first },
            stationsService.get().first { it.id == transport.linePosition.second },
            stationsService.getNextStationOnLine(
                lineStations = lineStations, linePosition = transport.linePosition)
        )
    }
}
