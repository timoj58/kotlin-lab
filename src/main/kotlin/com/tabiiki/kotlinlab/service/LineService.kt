package com.tabiiki.kotlinlab.service

import com.tabiiki.kotlinlab.controller.LineInformation
import com.tabiiki.kotlinlab.controller.LineStation
import com.tabiiki.kotlinlab.controller.TransporterInformation
import com.tabiiki.kotlinlab.factory.LineFactory
import com.tabiiki.kotlinlab.factory.StationFactory
import org.springframework.stereotype.Service

@Service
class LineService(
    private val lineFactory: LineFactory,
    private val stationFactory: StationFactory
) {

    fun getLineInformation(): List<LineInformation> =
        lineFactory.get().map { lineFactory.get(it) }.map {
            LineInformation(
                id = it.id,
                name = it.name,
                stations = it.stations.map { id ->
                    stationFactory.get(id)
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
