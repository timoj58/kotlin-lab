package com.tabiiki.kotlinlab.service

import com.tabiiki.kotlinlab.factory.LineFactory
import com.tabiiki.kotlinlab.factory.StationFactory
import com.tabiiki.kotlinlab.model.Transport
import com.tabiiki.kotlinlab.util.JourneyTimeRepoImpl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service


interface NetworkService {
    suspend fun start()
}

@Service
class NetworkServiceImpl(
    @Value("\${network.start-delay}") startDelay: Long,
    lineFactory: LineFactory,
    lineConductor: LineConductor,
) : NetworkService {

    private val lines = lineFactory.get().map { lineFactory.get(it) }
    private val controllers = mutableListOf<LineController>()

    init {
        lines.groupBy { it.name }.values.forEach { line ->
            controllers.add(
                LineControllerImpl(
                    startDelay,
                    line,
                    lineConductor,
                    JourneyTimeRepoImpl()
                )
            )
        }
    }

    override suspend fun start() = coroutineScope {
        controllers.forEach { controller ->
            val channel = Channel<Transport>()
            launch(Dispatchers.Default) { controller.start(channel) }
            launch(Dispatchers.Default) { controller.regulate(channel) }
        }
    }
}