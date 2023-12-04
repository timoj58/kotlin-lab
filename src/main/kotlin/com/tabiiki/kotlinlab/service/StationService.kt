package com.tabiiki.kotlinlab.service

import com.tabiiki.kotlinlab.controller.StationInformation
import com.tabiiki.kotlinlab.controller.StationLineInformation
import com.tabiiki.kotlinlab.factory.LineFactory
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
    val eventType: String = "STATION",
    val stationId: String? = null,
    val name: String? = null,
    val transportId: UUID? = null,
    val line: String? = null,
    val type: MessageType
)

@Service
class StationService(
    @Value("\${network.time-step}") val timeStep: Long,
    private val signalService: SignalService,
    private val stationFactory: StationFactory,
    private val lineFactory: LineFactory
) {
    private val stationMonitor = StationMonitor(timeStep = timeStep, stations = stationFactory.get())

    suspend fun start(
        globalListener: Channel<StationMessage>,
        commuterChannel: Channel<Commuter>,
        line: String? = null
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

    fun getInformation(): List<StationInformation> =
        stationFactory.get().map {
            val station = stationFactory.get(it)

            StationInformation(
                id = station.id,
                name = station.name,
                latitude = station.position.first,
                longitude = station.position.second,
                lines = lineFactory.getStationLines(it).map { line ->
                    StationLineInformation(
                        id = line.id,
                        name = line.name
                    )
                }
            )
        }.filter { it.lines.isNotEmpty() }
}
