package com.tabiiki.kotlinlab.service

import com.tabiiki.kotlinlab.factory.LineFactory
import com.tabiiki.kotlinlab.model.Transport
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.springframework.stereotype.Service
import java.util.UUID

interface NetworkService {
    suspend fun start(listener: Channel<StationMessage>)
    fun diagnostics(transports: List<UUID>)
}

@Service
class NetworkServiceImpl(
    private val lineController: LineController,
    private val stationService: StationService,
    lineFactory: LineFactory,
) : NetworkService {
    private val lines = lineFactory.get().map { lineFactory.get(it) }

    init {

        lineController.setStationChannels(
            listOf(lines).flatten().flatMap { it.stations }.distinct()
                .associateWith { stationService.getChannel(it) }
        )
    }

    override suspend fun start(listener: Channel<StationMessage>): Unit = coroutineScope {
        lines.groupBy { it.name }.values.forEach { line ->
            val channel = Channel<Transport>()
            launch { lineController.start(line, channel) }
        }
        launch { stationService.monitor(listener) }
    }

    override fun diagnostics(transports: List<UUID>) {
        lineController.diagnostics(transports)
    }

}