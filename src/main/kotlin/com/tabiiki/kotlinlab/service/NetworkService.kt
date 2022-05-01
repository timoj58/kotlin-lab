package com.tabiiki.kotlinlab.service

import com.tabiiki.kotlinlab.factory.LineFactory
import com.tabiiki.kotlinlab.model.Transport
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import javax.naming.ConfigurationException

interface NetworkService {
    suspend fun start(listener: Channel<StationMessage>)
}

@Service
class NetworkServiceImpl(
    @Value("\${network.start-delay}") startDelay: Long,
    private val stationService: StationService,
    lineFactory: LineFactory,
    platformConductor: LineConductor,
) : NetworkService {

    private val lines = lineFactory.get().map { lineFactory.get(it) }
    private val controllers = mutableListOf<LineController>()

    init {
        if (startDelay < 1000) throw ConfigurationException("start delay is to small, minimum 1000 ms")

        lines.groupBy { it.name }.values.forEach { line ->
            controllers.add(
                LineControllerImpl(
                    startDelay,
                    line,
                    platformConductor,
                    listOf(line).flatten().flatMap { it.stations }.distinct()
                        .associateWith { stationService.getChannel(it) }
                )
            )
        }
    }

    override suspend fun start(listener: Channel<StationMessage>): Unit = coroutineScope {
        controllers.forEach { controller ->
            val channel = Channel<Transport>()
            launch { controller.start(channel) }
        }
        launch { stationService.monitor(listener) }
    }

}