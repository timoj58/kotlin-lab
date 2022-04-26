package com.tabiiki.kotlinlab.service

import com.tabiiki.kotlinlab.model.Transport
import com.tabiiki.kotlinlab.repo.StationRepo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.springframework.stereotype.Service
import java.util.*

enum class MessageType {
    DEPART, ARRIVE
}

data class StationMessage(
    val stationId: String,
    val transportId: UUID,
    val line: String,
    val section: Pair<String, String>,
    val type: MessageType
)

interface StationService {
    fun getChannel(id: String): Channel<Transport>
    suspend fun monitor(listener: Channel<StationMessage>)
    suspend fun monitor(id: String, channel: Channel<Transport>, listener: Channel<StationMessage>)
}

@Service
class StationServiceImpl(
    stationRepo: StationRepo
) : StationService {

    //private val log = LoggerFactory.getLogger(this.javaClass)
    private val channels = stationRepo.get().map { it.id }.associateWith { Channel<Transport>() }

    override fun getChannel(id: String): Channel<Transport> {
        return channels[id]!!
    }

    override suspend fun monitor(listener: Channel<StationMessage>) = coroutineScope {
        channels.forEach { (k, v) ->
            launch(Dispatchers.Default) { monitor(k, v, listener) }
        }
    }

    override suspend fun monitor(id: String, channel: Channel<Transport>, listener: Channel<StationMessage>) {
        do {
            val message = channel.receive()
            if (waitingToArrive(id, message)) {
                listener.send(
                    StationMessage(
                        stationId = id,
                        transportId = message.id,
                        line = message.line.name,
                        section = message.section(),
                        type = MessageType.ARRIVE
                    )
                )
            } else if (waitingToDepart(id, message)) {
                listener.send(
                    StationMessage(
                        stationId = id,
                        transportId = message.id,
                        line = message.line.name,
                        section = message.section(),
                        type = MessageType.DEPART
                    )
                )
            }
        } while (true)
    }

    private fun waitingToDepart(id: String, message: Transport) =
        message.atPlatform() && message.section().first == id

    private fun waitingToArrive(id: String, message: Transport) =
        !message.atPlatform() && message.section().second == id

}