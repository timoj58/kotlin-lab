package com.tabiiki.kotlinlab.service

import com.tabiiki.kotlinlab.factory.LineFactory
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

    override suspend fun start(listener: Channel<StationMessage>): Unit = coroutineScope {
        lines.groupBy { it.name }.values.forEach { line ->
            launch { lineController.start(line) }
        }
        launch { stationService.start(listener) }
    }

    override fun diagnostics(transports: List<UUID>) = lineController.diagnostics(transports)
}