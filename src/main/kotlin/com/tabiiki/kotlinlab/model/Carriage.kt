package com.tabiiki.kotlinlab.model

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope

interface ICarriage {

    fun isEmpty(): Boolean
    suspend fun embark(commuterChannel: Channel<Commuter>)
    suspend fun disembark(station: String, commuterChannel: Channel<Commuter>)
}

data class Carriage(
    val capacity: Int
) : ICarriage {

    val channel: Channel<Commuter> = Channel()

    private val commuters = mutableListOf<Commuter>()
    override fun isEmpty(): Boolean = commuters.isEmpty()

    override suspend fun embark(commuterChannel: Channel<Commuter>) = coroutineScope {
        do {
            val commuter = channel.receive()
            if (commuters.size < capacity) {
                commuters.add(commuter)
            } else {
                commuterChannel.send(commuter)
            }
        } while (true)
    }

    override suspend fun disembark(station: String, commuterChannel: Channel<Commuter>) = coroutineScope {
        commuters.filter { Line.getStation(it.peekNextJourneyStage()!!.second) == station }.forEach { commuter ->
            commuter.let {
                it.completeJourneyStage()
                commuters.remove(it)
                commuterChannel.send(it)
            }
        }
    }
}
