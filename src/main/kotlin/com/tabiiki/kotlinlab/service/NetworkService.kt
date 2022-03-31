package com.tabiiki.kotlinlab.service

import com.tabiiki.kotlinlab.factory.LineFactory
import com.tabiiki.kotlinlab.model.Transport
import com.tabiiki.kotlinlab.repo.JourneyRepo
import com.tabiiki.kotlinlab.repo.TransporterTrackerRepo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

interface NetworkService {
    suspend fun start(listener: Channel<StationMessage>)
}

@Service
class NetworkServiceImpl(
    @Value("\${network.start-delay}") startDelay: Long,
    private val stationService: StationService,
    lineFactory: LineFactory,
    lineConductor: LineConductor,
    journeyRepo: JourneyRepo,
    private val transporterTrackerRepo: TransporterTrackerRepo
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
                    journeyRepo,
                    listOf(line).flatten().flatMap { it.stations }.distinct()
                        .associateWith { stationService.getChannel(it) },
                    transporterTrackerRepo
                )
            )
        }
    }

    override suspend fun start(listener: Channel<StationMessage>): Unit = coroutineScope {
        val trackerRepoChannel = Channel<Transport>()

        controllers.forEach { controller ->
            val channel = Channel<Transport>()

            launch(Dispatchers.Default) { controller.start(channel) }
            launch(Dispatchers.Default) { controller.regulate(channel, trackerRepoChannel) }
        }

        launch(Dispatchers.Default) { stationService.monitor(listener) }
        launch(Dispatchers.Default) { transporterTrackerRepo.track(trackerRepoChannel) }
    }

}