package com.tabiiki.kotlinlab.service

import com.tabiiki.kotlinlab.model.Line
import com.tabiiki.kotlinlab.model.Station
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
    ): Unit = coroutineScope {

        launch(Dispatchers.Default) {
            lineSectionService.release(transport,lineInstructions(transport, lineStations))
        }
    }

    private fun lineInstructions(transport: Transport, lineStations: List<String>): LineInstructions =
        LineInstructions(
            from = stationRepo.get(transport.section.first),
            to = stationRepo.get(transport.section.second),
            next =   stationRepo.getNextStationOnLine(
                lineStations = lineStations, section = transport.section
            ),
            direction = LineDirection.POSITIVE
        )

}
