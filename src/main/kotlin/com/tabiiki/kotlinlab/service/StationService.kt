package com.tabiiki.kotlinlab.service

import com.tabiiki.kotlinlab.factory.SignalMessage
import com.tabiiki.kotlinlab.factory.StationFactory
import com.tabiiki.kotlinlab.model.Commuter
import com.tabiiki.kotlinlab.model.Line
import com.tabiiki.kotlinlab.monitor.StationMonitor
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.util.UUID

enum class MessageType {
    DEPART, ARRIVE, HEALTH
}

data class StationMessage(
    val stationId: String? = null,
    val transportId: UUID? = null,
    val line: String? = null,
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
    private val stationMonitor = StationMonitor(timeStep = timeStep, stations = stationFactory.get())

    override suspend fun start(
        globalListener: Channel<StationMessage>,
        commuterChannel: Channel<Commuter>,
        line: String?
    ) = coroutineScope {
        launch { stationMonitor.monitorCommuters(commuterChannel) }
        launch { stationMonitor.healthTest(globalListener) }

        stationFactory.get().forEach { code ->
            val channel = Channel<SignalMessage>()
            val station = stationFactory.get(code)
            signalService.getPlatformSignals().filter { line == null || it.first.contains(line) }
                .filter { Line.getStation(it.second) == code }
                .map { signalService.getChannel(it) }.forEach { possibleChannel ->
                    possibleChannel?.let {
                        launch { stationMonitor.monitorPlatform(it, channel) }
                        launch { stationMonitor.monitorStation(station, channel, globalListener) }
                    }
                }
        }
    }

}