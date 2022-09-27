package com.tabiiki.kotlinlab.service

import com.tabiiki.kotlinlab.factory.LineFactory
import com.tabiiki.kotlinlab.model.Commuter
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.springframework.stereotype.Service

interface NetworkService {

    suspend fun init()
    suspend fun start(listener: Channel<StationMessage>)
}

@Service
class NetworkServiceImpl(
    private val lineController: LineController,
    private val stationService: StationService,
    private val commuterService: CommuterService,
    lineFactory: LineFactory,
) : NetworkService {
    private val lines = lineFactory.get().map { lineFactory.get(it) }
    override suspend fun init() = coroutineScope {
        lines.groupBy { it.name }.values.forEach { line ->
            launch { lineController.init(line, commuterService.getCommuterChannel()) }
        }
    }

    override suspend fun start(listener: Channel<StationMessage>): Unit = coroutineScope {
        launch { stationService.start(globalListener = listener, commuterChannel = commuterService.getCommuterChannel()) }

        lines.groupBy { it.name }.values.forEach { line ->
            launch { lineController.start(line) }
        }

        launch { commuterService.generate() }
    }
}