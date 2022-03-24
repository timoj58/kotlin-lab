package com.tabiiki.kotlinlab.service

import com.tabiiki.kotlinlab.factory.LineFactory
import com.tabiiki.kotlinlab.factory.StationFactory
import com.tabiiki.kotlinlab.model.Transport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.springframework.stereotype.Service


interface NetworkService {
    suspend fun start()
}

@Service
class NetworkServiceImpl(lineFactory: LineFactory, stationFactory: StationFactory): NetworkService {

    private val lines = lineFactory.get().map { lineFactory.get(it) }
    private val stationsService = StationsServiceImpl(stationFactory)
    private val controllers = mutableListOf<LineControllerService>()

    init {

        lines.groupBy { it.name }.values.forEach { line ->
            val conductor = ConductorImpl(stationsService)
            controllers.add(LineControllerService(line, conductor))
        }

    }

    override suspend fun start() = coroutineScope {
        controllers.forEach{controller ->
            val channel = Channel<Transport>()
            launch(Dispatchers.Default){controller.start(channel)}
            launch(Dispatchers.Default){controller.regulate(channel)}
        }
    }
}