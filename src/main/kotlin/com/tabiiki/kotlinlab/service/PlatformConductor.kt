package com.tabiiki.kotlinlab.service

import com.tabiiki.kotlinlab.model.Line
import com.tabiiki.kotlinlab.model.Status
import com.tabiiki.kotlinlab.model.Transport
import com.tabiiki.kotlinlab.repo.StationRepo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.springframework.stereotype.Service

interface PlatformConductor {
    fun getFirstTransportersToDispatch(lines: List<Line>): List<Transport>
    fun getNextTransportersToDispatch(lines: List<Line>): List<Transport>
    suspend fun hold(transport: Transport, lineStations: List<String>)
    suspend fun release(transport: Transport, lineStations: List<String>)
}

@Service
class PlatformConductorImpl(
    private val stationRepo: StationRepo,
    private val lineSectionService: LineSectionService
) : PlatformConductor {

    override fun getFirstTransportersToDispatch(lines: List<Line>): List<Transport> =
        lines.map { it.transporters }.flatten().groupBy { it.section }.values.flatten()
            .distinctBy { it.section }

    override fun getNextTransportersToDispatch(lines: List<Line>): List<Transport> =
        lines.map { it.transporters }.flatten().filter { it.status == Status.DEPOT }
            .groupBy { it.section }.values.flatten().distinctBy { it.section }

    override suspend fun hold(
        transport: Transport,
        lineStations: List<String>
    ): Unit = coroutineScope {
        var counter = 0
        do {
            delay(transport.timeStep)
            counter++
        } while (counter < 45)

        launch(Dispatchers.Default) { release(transport, lineStations) }
    }

    override suspend fun release(
        transport: Transport,
        lineStations: List<String>
    ) {
        transport.depart(
            stationRepo.get(transport.section.first),
            stationRepo.get(transport.section.second),
            stationRepo.getNextStationOnLine(
                lineStations = lineStations, section = transport.section
            )
        )
    }
}
