package com.tabiiki.kotlinlab.monitor

import com.tabiiki.kotlinlab.factory.SignalMessage
import com.tabiiki.kotlinlab.factory.SignalValue
import com.tabiiki.kotlinlab.model.Station
import com.tabiiki.kotlinlab.service.MessageType
import com.tabiiki.kotlinlab.service.StationMessage
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import java.util.UUID

class StationMonitor {

    suspend fun monitorPlatform(
        platformChannel: Channel<SignalMessage>,
        stationChannel: Channel<SignalMessage>,
    ) = coroutineScope {
        var previousSignal: SignalValue? = null
        do {
            val msg = platformChannel.receive()
            if(previousSignal == null || previousSignal != msg.signalValue) {
                stationChannel.send(msg)
                previousSignal = msg.signalValue
            }
        } while (true)
    }

    suspend fun monitorStation(
        station: Station,
        stationChannel: Channel<SignalMessage>,
        globalListener: Channel<StationMessage>,
    ) = coroutineScope {
        do {
            val msg = stationChannel.receive()
            msg.id?.let {
                val messageType = if (msg.signalValue == SignalValue.GREEN) MessageType.DEPART else MessageType.ARRIVE
                globalListener.send(
                    StationMessage(
                        stationId = station.id,
                        transportId = msg.id,
                        line = msg.line!!,
                        type = messageType
                    )
                )
            }
        } while (true)
    }

    //TODO ultimately need a companion object for station.
}