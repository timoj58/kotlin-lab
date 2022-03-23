package com.tabiiki.kotlinlab.service

import com.tabiiki.kotlinlab.model.Line
import com.tabiiki.kotlinlab.model.Transport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.util.*


interface LineControllerService {
    suspend fun start(channel: Channel<Transport>)
    fun regulate()
}

class LineControllerServiceImpl(
    private val line: List<Line>,
    private val stationsService: StationsService
) : LineControllerService {
    private val statuses = mutableMapOf<UUID, Boolean>()

    override suspend fun start(channel: Channel<Transport>) = coroutineScope {
        line.forEach { section ->
            section.transporters.groupBy { it.linePosition }.values.forEach {
                val transport = it.first()
                statuses[transport.id] = false

                launch(Dispatchers.Default) { transport.track(channel) }
                launch(Dispatchers.Default) {
                    transport.depart(
                        stationsService.get().first { it.id == transport.linePosition.first },
                        stationsService.get().first { it.id == transport.linePosition.second },
                        stationsService.getNextStation(transport.linePosition)
                    )
                }
            }
        }
    }

    override fun regulate() {

        TODO("Not yet implemented")
    }

}