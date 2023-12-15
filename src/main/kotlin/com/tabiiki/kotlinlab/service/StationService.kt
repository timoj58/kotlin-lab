package com.tabiiki.kotlinlab.service

import com.tabiiki.kotlinlab.controller.StationInformation
import com.tabiiki.kotlinlab.controller.StationLineInformation
import com.tabiiki.kotlinlab.factory.LineFactory
import com.tabiiki.kotlinlab.factory.SignalMessageV2
import com.tabiiki.kotlinlab.factory.SignalV2
import com.tabiiki.kotlinlab.factory.StationFactory
import com.tabiiki.kotlinlab.model.Commuter
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
    private val stationFactory: StationFactory,
    private val lineFactory: LineFactory
) {
    private val stationMonitor = StationMonitor(timeStep = timeStep, stations = stationFactory.get())
    private val subscribers = mutableMapOf<Pair<String, String>, Channel<SignalMessageV2>>()
    fun getSubscribers(platformSignals: List<SignalV2>): Map<Pair<String, String>, Channel<SignalMessageV2>> {
        platformSignals.forEach {
            subscribers[it.key] = Channel()
        }

        return subscribers
    }

    suspend fun start(
        globalListener: Channel<StationMessage>,
        commuterChannel: Channel<Commuter>
    ) = coroutineScope {
        launch { stationMonitor.monitorCommuters(commuterChannel) }
        launch { stationMonitor.healthTest(globalListener) }

        stationFactory.get().map { stationFactory.get(it) }.forEach { station ->
            val channel = Channel<SignalMessageV2>()
            launch {
                stationMonitor.monitorStation(
                    station = station,
                    stationChannel = channel,
                    globalListener = globalListener
                )
            }

            subscribers.filter { it.key.second.substringAfter(":") == station.id }.forEach {
                launch {
                    stationMonitor.monitorPlatform(
                        platformChannel = it.value,
                        stationChannel = channel
                    )
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
