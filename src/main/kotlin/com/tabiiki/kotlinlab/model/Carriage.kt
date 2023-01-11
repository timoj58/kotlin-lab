package com.tabiiki.kotlinlab.model

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive

interface ICarriage {

    fun isEmpty(): Boolean
    suspend fun embark(stationChannel: Channel<Commuter>)
    suspend fun disembark(station: String, stationChannel: Channel<Commuter>)
}

data class Carriage(
    val capacity: Int
) : ICarriage {

    val channel: Channel<Commuter> = Channel()

    private val commuters = mutableListOf<Commuter>()
    override fun isEmpty(): Boolean = commuters.isEmpty()

    override suspend fun embark(stationChannel: Channel<Commuter>) = coroutineScope {
        do {
            val commuter = channel.receive()
            if (commuters.size < capacity) commuters.add(commuter) else stationChannel.send(commuter)
        } while (true)
    }

    override suspend fun disembark(station: String, stationChannel: Channel<Commuter>) = coroutineScope {
        commuters.filter { Line.getStation(it.peekNextJourneyStage()!!.second) == station }.forEach { commuter ->
            commuter.let {
                it.completeJourneyStage()
                commuters.remove(it)
                stationChannel.send(it)
            }
        }
    }

}