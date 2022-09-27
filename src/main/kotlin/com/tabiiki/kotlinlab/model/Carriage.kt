package com.tabiiki.kotlinlab.model

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope

interface ICarriage {

    fun isEmpty(): Boolean
    fun getChannel(): Channel<Commuter>
    suspend fun embark(channel: Channel<Commuter>)
    suspend fun disembark(station: String, channel: Channel<Commuter>)
}

data class Carriage(
    val capacity: Int
) : ICarriage {

    private val commuters = mutableListOf<Commuter>()
    override fun isEmpty(): Boolean = commuters.isEmpty()

    override fun getChannel() = channel

    override suspend fun embark(channel: Channel<Commuter>) = coroutineScope {
        do {
            val commuter = channel.receive()
            if (commuters.size < capacity) commuters.add(commuter) else channel.send(commuter)
        } while (true)
    }

    override suspend fun disembark(station: String, channel: Channel<Commuter>) = coroutineScope {
        commuters.filter { it.journey().second == station }.forEach {
            commuters.remove(it)
            channel.send(it)
        }
    }

    companion object {
        val channel: Channel<Commuter> = Channel()
    }
}