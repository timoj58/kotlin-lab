package com.tabiiki.kotlinlab.service

import com.tabiiki.kotlinlab.factory.SignalMessageV2
import com.tabiiki.kotlinlab.factory.SignalV2
import com.tabiiki.kotlinlab.model.Commuter
import com.tabiiki.kotlinlab.model.Line
import com.tabiiki.kotlinlab.model.Transport
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.springframework.stereotype.Service

@Service
class LineConductor(
    private val platformService: PlatformServiceV2
) {

    fun getPlatformSignals(): List<SignalV2> = platformService.getPlatformSignals()
    fun getTransportersToDispatch(lines: List<Line>): MutableList<Transport> =
        lines.map { it.transporters }.flatten().groupBy { it.section() }.values.flatten().toMutableList()

    suspend fun release(
        transport: Transport
    ): Unit = coroutineScope {
        platformService.release(transport = transport)
    }

    suspend fun buffer(
        transporters: MutableList<Transport>
    ) = coroutineScope {
        platformService.release(transporters = transporters)
    }

    suspend fun subscribeStations(
        stationSubscribers: Map<Pair<String, String>, Channel<SignalMessageV2>>
    ) = coroutineScope {
        launch {
            platformService.subscribeStations(
                stationSubscribers = stationSubscribers
            )
        }
    }

    suspend fun init(
        commuterChannel: Channel<Commuter>
    ) = coroutineScope {
        launch {
            platformService.init(
                commuterChannel = commuterChannel
            )
        }
    }
}
