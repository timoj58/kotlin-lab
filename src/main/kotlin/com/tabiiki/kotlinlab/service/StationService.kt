package com.tabiiki.kotlinlab.service

import com.tabiiki.kotlinlab.model.Transport
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.*

enum class MessageType {
    DEPART, ARRIVE
}

data class StationMessage(
    val stationId: String,
    val transportId: UUID,
    val section: Pair<String, String>,
    val type: MessageType)

interface StationService {
    fun getDepartures(id: String): Set<StationMessage>
    fun getArrivals(id: String): Set<StationMessage>
    fun getChannel(id: String): Channel<Transport>
    suspend fun monitor(listener: Channel<StationMessage>)
    suspend fun monitor(id: String, channel: Channel<Transport>)
}

@Service
class StationServiceImpl(stationsService: StationsService): StationService {

    //private val log = LoggerFactory.getLogger(this.javaClass)

    private val channels = stationsService.get().map { it.id}.associateWith { Channel<Transport>() }
    private val messages = stationsService.get().map { it.id }.associateWith { mutableSetOf<StationMessage>() }

    override fun getDepartures(id: String): Set<StationMessage> {
        return messages[id]!!.filter { it.type == MessageType.DEPART }.toSet()
    }

    override fun getArrivals(id: String): Set<StationMessage> {
        return messages[id]!!.filter { it.type == MessageType.ARRIVE }.toSet()
    }

    override fun getChannel(id: String): Channel<Transport> {
        return channels[id]!!
    }

    override suspend fun monitor(listener: Channel<StationMessage>) = coroutineScope {
        async { status(listener) }
        channels.forEach { (k, v) ->
            launch(Dispatchers.Default) { monitor(k, v)}
        }
    }

    override suspend fun monitor(id: String, channel: Channel<Transport>){
        do {
            val message = channel.receive()
            if(!message.isStationary() && message.linePosition.second == id ){
                messages[id]?.add(StationMessage(
                    stationId = id,
                    transportId = message.id,
                    section = message.linePosition,
                    type = MessageType.ARRIVE))
                messages[message.linePosition.first]?.removeIf{it.transportId == message.id && it.type == MessageType.DEPART}
            }else if (message.isStationary() && message.linePosition.first == id){
                messages[id]?.add(StationMessage(
                    stationId = id,
                    transportId = message.id,
                    section = message.linePosition,
                    type = MessageType.DEPART))
                messages[message.linePosition.second]?.removeIf {it.transportId == message.id && it.type == MessageType.ARRIVE}
            }
        }while (true)
    }

    private suspend fun status(listener: Channel<StationMessage>) = coroutineScope {
        do {
            delay(1000)
            messages.values.flatten().forEach { listener.send(it) }

        }while (true)
    }

}