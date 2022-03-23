package com.tabiiki.kotlinlab.service

import com.tabiiki.kotlinlab.model.Line
import com.tabiiki.kotlinlab.model.Station
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.util.*

interface LineControllerService {
    suspend fun start()
    fun regulate()
    fun areAnyTransportsRunning(): Boolean
}

class LineControllerServiceImpl(
    private val line: List<Line>,
    private val stations: List<Station>
) : LineControllerService {
    private val channel = Channel<Pair<UUID, Boolean>>()
    private val statuses = mutableMapOf<UUID, Boolean>()

    override suspend fun start() = coroutineScope {
        launch(Dispatchers.Default) {
            statusListener()
        }
        line.forEach { section ->
            section.transporters.groupBy { it.linePosition }.values.forEach {
                val transport = it.first()
                statuses[transport.id] = false

                launch(Dispatchers.Default) { transport.sendCurrentState(channel) }
                launch(Dispatchers.Default) {
                    transport.depart(
                        stations.first { it.id == transport.linePosition.first },
                        stations.first { it.id == transport.linePosition.second }
                    )
                }
            }
        }
    }

    override fun regulate() {
        TODO("Not yet implemented")
    }

    override fun areAnyTransportsRunning(): Boolean = statuses.values.toList().any { it }

    private suspend fun statusListener() {
        while (true) {
            val status = channel.receive()
            if (statuses[status.first] != status.second) statuses[status.first] = status.second
        }
    }

}