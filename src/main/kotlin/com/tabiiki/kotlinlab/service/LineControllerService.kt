package com.tabiiki.kotlinlab.service

import com.tabiiki.kotlinlab.model.Line
import com.tabiiki.kotlinlab.model.Station
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
    private val stations: List<Station>
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
                        stations.first { it.id == transport.linePosition.first },
                        stations.first { it.id == transport.linePosition.second },
                        getNextStation(transport.linePosition)
                    )
                }
            }
        }
    }

    override fun regulate() {
        TODO("Not yet implemented")
    }

    private fun getNextStation(linePosition: Pair<String, String>): Station {

        val stationCodes = stations.map { it.id }
        val fromStationIdx = stationCodes.indexOf(linePosition.first)
        val toStationIdx = stationCodes.indexOf(linePosition.second)
        val direction = fromStationIdx - toStationIdx

        return if (direction > 0) if (toStationIdx > 0) stations[toStationIdx - 1] else stations[1] else
            if (toStationIdx < stations.size - 1) stations[toStationIdx + 1] else stations.reversed()[1]

    }
}