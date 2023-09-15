package com.tabiiki.kotlinlab.service

import com.google.gson.Gson
import com.tabiiki.kotlinlab.model.TransportMessage
import com.tabiiki.kotlinlab.websocket.KotlinLabSocketHandler
import kotlinx.coroutines.channels.Channel
import org.springframework.stereotype.Service

@Service
class MessageService(
    private val kotlinLabSocketHandler: KotlinLabSocketHandler
) {
    val stationReceiver = Channel<StationMessage>()
    val transportReceiver = Channel<TransportMessage>()

    suspend fun stationTracker() {
        do {
            val msg = stationReceiver.receive()
            kotlinLabSocketHandler.send(
                gson.toJson(msg)
            )
        } while (true)
    }

    suspend fun transportTracker() {
        do {
            val msg = transportReceiver.receive()
            kotlinLabSocketHandler.send(
                gson.toJson(msg)
            )
        } while (true)
    }

    companion object {
        var gson = Gson()
    }
}
