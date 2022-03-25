package com.tabiiki.kotlinlab.service

import com.tabiiki.kotlinlab.model.Transport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

interface LineConductor {
    suspend fun hold(transport: Transport, delay: Int)
    suspend fun depart(transport: Transport)
}

class LineConductorImpl(private val stationsService: StationsService) : LineConductor {
    override suspend fun hold(transport: Transport, delay: Int): Unit = coroutineScope {
        if (transport.holdCounter > delay) launch(Dispatchers.Default) { depart(transport) }
    }

    override suspend fun depart(transport: Transport) {
        transport.depart(
            stationsService.get().first { it.id == transport.linePosition.first },
            stationsService.get().first { it.id == transport.linePosition.second },
            stationsService.getNextStation(transport.linePosition)
        )
    }
}
