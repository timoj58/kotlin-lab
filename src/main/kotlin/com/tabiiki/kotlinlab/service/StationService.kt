package com.tabiiki.kotlinlab.service

import com.tabiiki.kotlinlab.model.Transport
import com.tabiiki.kotlinlab.repo.StationRepo
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
    fun getChannel(id: String): Channel<Transport>
    suspend fun monitor(listener: Channel<StationMessage>)
    suspend fun monitor(id: String, channel: Channel<Transport>)
}

@Service
class StationServiceImpl(
    @Value("\${network.time-step}") private val timeStep: Long,
    stationRepo: StationRepo
) : StationService {

    //private val log = LoggerFactory.getLogger(this.javaClass)

    private val channels = stationRepo.get().map { it.id }.associateWith { Channel<Transport>() }
    private val messageQueue = ArrayDeque<StationMessage>()

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
                messageQueue.push(
                    StationMessage(
                        stationId = id,
                        transportId = message.id,
                        lineId = message.lineId,
                        section = message.linePosition,
                        type = MessageType.ARRIVE
                    )
                )
            } else if (waitingToDepart(id, message)) {
                messageQueue.push(
                    StationMessage(
                        stationId = id,
                        transportId = message.id,
                        lineId = message.lineId,
                        section = message.linePosition,
                        type = MessageType.DEPART
                    )
                )
            }
        } while (true)
    }

    private fun waitingToDepart(id: String, message: Transport) =
        message.isStationary() && message.linePosition.first == id

    private fun waitingToArrive(id: String, message: Transport) =
        !message.isStationary() && message.linePosition.second == id

    private suspend fun status(listener: Channel<StationMessage>) = coroutineScope {
        do {
            delay(timeStep)
            while (!messageQueue.isEmpty())
                listener.send(messageQueue.pop())
        } while (true)
    }

}