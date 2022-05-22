package com.tabiiki.kotlinlab.model

import com.tabiiki.kotlinlab.repo.StationRepo
import com.tabiiki.kotlinlab.service.LineInstructions
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class Lines(private val stationRepo: StationRepo) {

    private val lineDetails: ConcurrentHashMap<String, List<Line>> = ConcurrentHashMap()
    private val lineStations: ConcurrentHashMap<UUID, List<String>> = ConcurrentHashMap()

    fun addLineDetails(key: String, details: List<Line>) { lineDetails[key] = details }

    fun lineInstructions(transport: Transport): LineInstructions =
        LineInstructions(
            from = stationRepo.get(transport.getSectionStationCode()),
            to = stationRepo.get(transport.section().second),
            next = stationRepo.getNextStationOnLine(
                lineStations = getLineStations(transport),
                section = Pair(transport.getSectionStationCode(), transport.section().second)
            ),
            direction = transport.lineDirection()
        )

    private fun getLineStations(transport: Transport): List<String> {
        if (!lineStations.contains(transport.id))
            lineStations[transport.id] =
                lineDetails[transport.line.name]!!.first { l -> l.transporters.any { it.id == transport.id } }.stations
        return lineStations[transport.id]!!
    }
}
