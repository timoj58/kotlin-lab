package com.tabiiki.kotlinlab.service

import com.tabiiki.kotlinlab.controller.LineInformation
import com.tabiiki.kotlinlab.controller.LineStation
import com.tabiiki.kotlinlab.controller.TransporterInformation
import com.tabiiki.kotlinlab.factory.LineFactory
import com.tabiiki.kotlinlab.model.Line
import com.tabiiki.kotlinlab.model.Station
import com.tabiiki.kotlinlab.model.Transport
import com.tabiiki.kotlinlab.repo.StationRepo
import org.springframework.stereotype.Service
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
    val minimumHold: Int
)

@Service
class LineService(
    private val lineFactory: LineFactory,
    private val stationRepo: StationRepo
) {
    private val lineDetails: ConcurrentHashMap<String, List<Line>> = ConcurrentHashMap()
    private val lineStations: ConcurrentHashMap<UUID, List<String>> = ConcurrentHashMap()

    init {
        lineFactory.get().map { lineFactory.get(it) }.groupBy { it.name }.values.forEach { line ->
            lineDetails[line.first().name] = line
        }
    }

    fun getLineInstructions(transport: Transport, minimumHold: Int): LineInstructions = LineInstructions(
        from = stationRepo.get(transport.getSectionStationCode()),
        to = stationRepo.get(transport.section().second),
        next = stationRepo.getNextStationOnLine(
            lineStations = getLineStations(transport),
            section = Pair(transport.getSectionStationCode(), transport.section().second)
        ),
        direction = transport.lineDirection(),
        minimumHold = minimumHold
    )

    fun getLines(): List<Line> = lineFactory.get().map { lineFactory.get(it) }

    fun isSwitchStation(line: String, station: String): Boolean = lineFactory.isSwitchStation(line, station)

    fun getLineStations(transport: Transport): List<String> {
        if (!lineStations.contains(transport.id)) {
            lineStations[transport.id] =
                lineDetails[transport.line.name]!!.first { l -> l.transporters.any { it.id == transport.id } }.stations
        }
        return lineStations[transport.id]!!
    }

    fun getPreviousSections(platformKey: Pair<String, String>): List<Pair<String, String>> {
        val line = Line.getLine(platformKey.first)
        val direction = platformKey.first.substringAfter(":").substringBefore(":")
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

    fun getLineInformation(): List<LineInformation> =
        lineFactory.get().map { lineFactory.get(it) }.map {
            LineInformation(
                id = it.id,
                name = it.name,
                stations = it.stations.map { id ->
                    stationRepo.get(id)
                }.map { station ->
                    LineStation(
                        id = station.id,
                        name = station.name,
                        latitude = station.position.first,
                        longitude = station.position.second

                    )
                }
            )
        }.sortedBy { it.id }

    fun getTransporterInformation(): List<TransporterInformation> =
        lineFactory.get().map { lineFactory.get(it) }.map {
            it.transporters.map { transporter ->
                TransporterInformation(
                    id = transporter.id,
                    type = it.transportType.toString()
                )
            }
        }.flatten()
}
