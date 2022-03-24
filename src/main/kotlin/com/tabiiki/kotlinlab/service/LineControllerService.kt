package com.tabiiki.kotlinlab.service

import com.tabiiki.kotlinlab.model.Line
import com.tabiiki.kotlinlab.model.Transport
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel

interface Conductor {
    suspend fun hold(transport: Transport)
    suspend fun depart(transport: Transport)
}

class ConductorImpl(private val stationsService: StationsService): Conductor {
    override suspend fun hold(transport: Transport): Unit = coroutineScope {
        if(transport.holdCounter > 15) launch(Dispatchers.Default) { depart(transport) }
    }

    override suspend fun depart(transport: Transport) {
        transport.depart(
            stationsService.get().first { it.id == transport.linePosition.first },
            stationsService.get().first { it.id == transport.linePosition.second },
            stationsService.getNextStation(transport.linePosition)
        )
    }
}

interface LineControllerService {
    suspend fun start(channel: Channel<Transport>)
    suspend fun regulate(channel: Channel<Transport>)
}

class LineControllerServiceImpl(
    private val line: List<Line>,
    private val conductor: Conductor
) : LineControllerService {

    override suspend fun start(channel: Channel<Transport>) = coroutineScope {
        line.forEach { section ->
            section.transporters.groupBy { it.linePosition }.values.forEach {
                val transport = it.first()

                launch(Dispatchers.Default) { transport.track(channel) }
                launch(Dispatchers.Default) { conductor.depart(transport) }
            }
        }
        //TODO tricky part.  how to release the next trains into it.
    }

    override suspend fun regulate(channel: Channel<Transport>) = coroutineScope {
        do {
            val message = channel.receive()
            if (message.isStationary()) async { conductor.hold(message) }
        } while (true)
    }

}