package com.tabiiki.kotlinlab.service

import com.tabiiki.kotlinlab.factory.SignalMessage
import com.tabiiki.kotlinlab.factory.StationFactory
import com.tabiiki.kotlinlab.model.Commuter
import com.tabiiki.kotlinlab.model.Station
import com.tabiiki.kotlinlab.monitor.StationMonitor
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

enum class MessageType {
    DEPART, ARRIVE
}

data class StationMessage(
    val stationId: String,
    val transportId: UUID,
    val line: String,
    val type: MessageType
)

interface StationService {
    suspend fun start(
        globalListener: Channel<StationMessage>,
        commuterChannel: Channel<Commuter>,
        line: String? = null,
    )
}

@Service
class StationServiceImpl(
    @Value("\${network.time-step}") val timeStep: Long,
    private val signalService: SignalService,
    private val stationFactory: StationFactory
) : StationService {
    private val stationChannels: ConcurrentHashMap<Station, Channel<SignalMessage>> = ConcurrentHashMap()
    private val stationMonitor = StationMonitor()

    override suspend fun start(
        globalListener: Channel<StationMessage>,
        commuterChannel: Channel<Commuter>,
        line: String?
    ) = coroutineScope {
        launch { stationMonitor.monitorCommuters(commuterChannel) }

        stationFactory.get().forEach { code ->
            val channel = Channel<SignalMessage>()
            val station = stationFactory.get(code)
            stationChannels[station] = channel
            signalService.getPlatformSignals().filter { line == null || it.first.contains(line) }
                .filter { it.second.substringAfter(":") == code }
                .map { signalService.getChannel(it) }.forEach { possibleChannel ->
                    possibleChannel?.let {
                        launch { stationMonitor.monitorPlatform(it, channel) }
                        launch { stationMonitor.monitorStation(station, channel, globalListener) }
                    }
                }
        }
    }

}