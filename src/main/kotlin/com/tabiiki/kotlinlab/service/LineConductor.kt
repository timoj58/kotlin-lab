package com.tabiiki.kotlinlab.service

import com.tabiiki.kotlinlab.model.Line
import com.tabiiki.kotlinlab.model.Status
import com.tabiiki.kotlinlab.model.Transport
import com.tabiiki.kotlinlab.repo.StationRepo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.springframework.stereotype.Service

interface LineConductor {
    fun getFirstTransportersToDispatch(lines: List<Line>): List<Transport>
    fun getNextTransportersToDispatch(lines: List<Line>): List<Transport>
    suspend fun hold(transport: Transport, delay: Int, lineStations: List<String>)
    suspend fun depart(transport: Transport, lineStations: List<String>)
}

@Service
class LineConductorImpl(private val stationRepo: StationRepo) : LineConductor {
    override fun getFirstTransportersToDispatch(lines: List<Line>): List<Transport> =
        lines.map { it.transporters }.flatten().groupBy { it.linePosition }.values.flatten().distinctBy { it.linePosition }

    override fun getNextTransportersToDispatch(lines: List<Line>): List<Transport> =
        lines.map { it.transporters }.flatten().filter { it.status == Status.DEPOT }.groupBy { it.linePosition }.values.flatten().distinctBy { it.linePosition }

    override suspend fun hold(
        transport: Transport,
        delay: Int,
        lineStations: List<String>): Unit = coroutineScope {
        if (transport.holdCounter > delay) launch(Dispatchers.Default) { depart(transport, lineStations) }
    }

    override suspend fun depart(transport: Transport, lineStations: List<String>) {
        transport.depart(
            stationRepo.get(transport.linePosition.first),
            stationRepo.get(transport.linePosition.second),
            stationRepo.getNextStationOnLine(
                lineStations = lineStations, linePosition = transport.linePosition
            )
        )
    }
}
