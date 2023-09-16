package com.tabiiki.kotlinlab.repo

import com.tabiiki.kotlinlab.model.Line
import com.tabiiki.kotlinlab.model.Station
import com.tabiiki.kotlinlab.model.Transport
import org.springframework.stereotype.Repository
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

enum class LineDirection {
    POSITIVE, NEGATIVE, TERMINAL
}

data class LineInstructions(
    val from: Station,
    val to: Station,
    val next: Station,
    val direction: LineDirection,
    val minimumHold: Int = 45
)

@Repository
class LineRepo(private val stationRepo: StationRepo) {

    private val lineDetails: ConcurrentHashMap<String, List<Line>> = ConcurrentHashMap()
    private val lineStations: ConcurrentHashMap<UUID, List<String>> = ConcurrentHashMap()

    fun addLineDetails(key: String, details: List<Line>) {
        lineDetails[key] = details
    }

    fun getLineInstructions(transport: Transport): LineInstructions = LineInstructions(
        from = stationRepo.get(transport.getSectionStationCode()),
        to = stationRepo.get(transport.section().second),
        next = stationRepo.getNextStationOnLine(
            lineStations = getLineStations(transport),
            section = Pair(transport.getSectionStationCode(), transport.section().second)
        ),
        direction = transport.lineDirection()
    )

    fun getLineStations(transport: Transport): List<String> {
        if (!lineStations.contains(transport.id)) {
            lineStations[transport.id] =
                lineDetails[transport.line.name]!!.first { l -> l.transporters.any { it.id == transport.id } }.stations
        }
        return lineStations[transport.id]!!
    }

    fun getPreviousSections(platformKey: Pair<String, String>): List<Pair<String, String>> {
        val line = Line.getLine(platformKey.first)
        val direction = platformKey.first.substringAfter(":")
        val stationTo = Line.getStation(platformKey.second)
        val stationsFrom = stationRepo.getPreviousStationsOnLine(
            getLineStations(line),
            stationTo,
            LineDirection.valueOf(direction)
        )

        return stationsFrom.map { Pair("$line:${it.id}", stationTo) }.distinct()
    }

    fun getLineStations(line: String): List<Line> =
        lineDetails[line] ?: throw RuntimeException("missing $line")
}
