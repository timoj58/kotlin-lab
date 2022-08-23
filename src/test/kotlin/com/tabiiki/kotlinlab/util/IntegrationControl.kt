package com.tabiiki.kotlinlab.util

import com.tabiiki.kotlinlab.model.Line
import com.tabiiki.kotlinlab.service.MessageType
import com.tabiiki.kotlinlab.service.StationMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import org.assertj.core.api.Assertions
import java.util.UUID
import java.util.function.Consumer

class IntegrationControl {
    private val stationVisitedPerTrain = mutableMapOf<UUID, MutableList<String>>()
    private val trainsByLine = mutableMapOf<String, MutableSet<UUID>>()
    private val stationsByLine = mutableMapOf<String, Set<String>>()
    private var transportersPerLine = 0

    fun initControl(line: Line) {
        stationsByLine[line.id] = line.stations.toSet()
        transportersPerLine += line.transporters.size
    }

    suspend fun status(channel: Channel<StationMessage>, jobs: List<Job>, timeout: Int) {
        val startTime = System.currentTimeMillis()
        do {
            val msg = channel.receive()
            if (!trainsByLine.containsKey(msg.line))
                trainsByLine[msg.line] = mutableSetOf()
            if (!stationVisitedPerTrain.containsKey(msg.transportId))
                stationVisitedPerTrain[msg.transportId] = mutableListOf()

            trainsByLine[msg.line]?.add(msg.transportId)
            if (msg.type == MessageType.DEPART)
                stationVisitedPerTrain[msg.transportId]?.add(msg.stationId)

        } while (testSectionsVisited() != transportersPerLine && startTime + (1000 * 60 * timeout) > System.currentTimeMillis())

        diagnosticsCheck()
        jobs.forEach { it.cancelAndJoin() }
        assert()

    }

    private fun diagnosticsCheck(): List<UUID> {
        val toLog = mutableListOf<UUID>()
        stationVisitedPerTrain.forEach { (k, u) ->
            val line = getLineByTrain(k)
            val total = stationsByLine[line]!!.toList()
            println("$line - $k visited ${u.size} vs ${total.size}")

            if (u.size != total.size) toLog.add(k)
        }
        return toLog
    }

    private fun assert() {
        println("total trains: $transportersPerLine, trains running: ${stationVisitedPerTrain.keys.size}  and stations visited ${stationVisitedPerTrain.values.flatten().size}")
        val count = testSectionsVisited()
        println("completed journeys count: $count")

        Assertions.assertThat(count).isEqualTo(transportersPerLine)
    }

    private fun testSectionsVisited(): Int {
        var completedRouteCount = 0

        if (stationVisitedPerTrain.isEmpty()) return 1

        stationVisitedPerTrain.forEach { (k, u) ->
            if (u.containsAll(stationsByLine[getLineByTrain(k)]!!) && testAllStationsEntered(u))
                completedRouteCount++
        }

        return completedRouteCount +
                if (trainsByLine.values.flatten().size == transportersPerLine) 0 else 1
    }

    private fun testAllStationsEntered(stations: List<String>): Boolean {
        return stations.groupBy { it }.values.flatten().count() + 1 >= stations.distinct().size * 2
    }

    private fun getLineByTrain(id: UUID): String {
        var line = ""
        trainsByLine.forEach { (t, u) ->
            if (u.contains(id)) t.also { line = it }

        }
        return line
    }
}
