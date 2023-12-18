package com.tabiiki.kotlinlab.model

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

data class Carriage(
    val capacity: Int
) {
    private val commuters = mutableListOf<Commuter>()
    fun isEmpty(): Boolean = commuters.isEmpty()

    suspend fun embark(
        embarkChannel: Channel<Commuter>,
        commuterChannel: Channel<Commuter>
    ) = coroutineScope {
        do {
            val commuter = embarkChannel.receive()
            if (commuters.size < capacity) {
                commuters.add(commuter)
            } else {
                launch { commuterChannel.send(commuter) }
            }
        } while (true)
    }

    suspend fun disembark(station: String, commuterChannel: Channel<Commuter>) = coroutineScope {
        commuters.filter { Line.getStation(it.peekNextJourneyStage()!!.second) == station }.forEach { commuter ->
            commuter.let {
                it.completeJourneyStage()
                commuters.remove(it)
                launch { commuterChannel.send(it) }
            }
        }
    }
}
