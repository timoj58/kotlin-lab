package com.tabiiki.kotlinlab.service

import com.tabiiki.kotlinlab.model.Transport
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.util.*

enum class MessageType {
    DEPART, ARRIVE
}

data class StationMessage(
    val stationId: String,
    val transportId: UUID,
    val lineId: String,
    val section: Pair<String, String>,
    val type: MessageType
)

interface StationService {
    fun getDepartures(id: String): Set<StationMessage>
    fun getArrivals(id: String): Set<StationMessage>
    fun getChannel(id: String): Channel<Transport>
    suspend fun monitor(listener: Channel<StationMessage>)
    suspend fun monitor(id: String, channel: Channel<Transport>)
}

@Service
class StationServiceImpl(
    @Value("\${network.time-step}") private val timeStep: Long,
    stationsService: StationsService
) : StationService {

    //private val log = LoggerFactory.getLogger(this.javaClass)

    private val channels = stationsService.get().map { it.id }.associateWith { Channel<Transport>() }
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
            launch(Dispatchers.Default) { monitor(k, v) }
        }
    }

    override suspend fun monitor(id: String, channel: Channel<Transport>) {
        do {
            val message = channel.receive()
            if (waitingToArrive(id, message)) {
                messages[id]?.add(
                    StationMessage(
                        stationId = id,
                        transportId = message.id,
                        lineId = message.lineId,
                        section = message.linePosition,
                        type = MessageType.ARRIVE
                    )
                )
                removePreviousState(message.linePosition.first, message.id, MessageType.DEPART)
            } else if (waitingToDepart(id, message)) {
                messages[id]?.add(
                    StationMessage(
                        stationId = id,
                        transportId = message.id,
                        lineId = message.lineId,
                        section = message.linePosition,
                        type = MessageType.DEPART
                    )
                )
                removePreviousState(message.linePosition.second, message.id, MessageType.ARRIVE)
            }
        } while (true)
    }

    private fun waitingToDepart(id: String, message: Transport) = message.isStationary() && message.linePosition.first == id
    private fun waitingToArrive(id: String, message: Transport) = !message.isStationary() && message.linePosition.second == id
    private fun removePreviousState(key: String, transportId: UUID, type: MessageType) = messages[key]?.removeIf{it.transportId == transportId && it.type == type}

    private suspend fun status(listener: Channel<StationMessage>) = coroutineScope {
        do {
            delay(timeStep)
            messages.values.flatten().forEach { listener.send(it) }

        } while (true)
    }

}