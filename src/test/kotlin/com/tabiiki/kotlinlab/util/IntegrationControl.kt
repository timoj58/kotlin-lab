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
    private val stationVisitedPerTrain = mutableMapOf<UUID, MutableSet<Pair<String, String>>>()
    private val trainsByLine = mutableMapOf<String, MutableSet<UUID>>()
    private val sectionsByLine = mutableMapOf<String, Set<Pair<String, String>>>()
    private var transportersPerLine = 0

    fun initControl(line: Line) {
        sectionsByLine[line.id] = getLineStations(line)
        transportersPerLine += line.transporters.size
    }

    suspend fun status(channel: Channel<StationMessage>, jobs: List<Job>, timeout: Int, dump: Consumer<List<UUID>>) {
        val startTime = System.currentTimeMillis()
        do {
            val msg = channel.receive()
            if (!trainsByLine.containsKey(msg.line.id))
                trainsByLine[msg.line.id] = mutableSetOf()
            if (!stationVisitedPerTrain.containsKey(msg.transportId))
                stationVisitedPerTrain[msg.transportId] = mutableSetOf()

            trainsByLine[msg.line.id]?.add(msg.transportId)
            if (msg.type == MessageType.ARRIVE)
                stationVisitedPerTrain[msg.transportId]?.add(msg.section)
        } while (testSectionsVisited() != transportersPerLine && startTime + (1000 * 60 * timeout) > System.currentTimeMillis())

        dump.accept(diagnosticsCheck())
        jobs.forEach { it.cancelAndJoin() }
        assert()

    }

    private fun diagnosticsCheck(): List<UUID> {
        val toLog = mutableListOf<UUID>()
        stationVisitedPerTrain.forEach { (k, u) ->
            val line = getLineByTrain(k)
            val total = sectionsByLine[line]!!.toList()
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
        stationVisitedPerTrain.values.flatten()
            .forEach {
                if (it.second == it.first) println("incorrect route $it")
                Assertions.assertThat(it.second == it.first).isEqualTo(false)
            }
    }

    private fun testSectionsVisited(): Int {
        var completedRouteCount = 0

        if (stationVisitedPerTrain.isEmpty()) return 1

        stationVisitedPerTrain.forEach { (k, u) ->
            if (u.size >= sectionsByLine[getLineByTrain(k)]!!.size) // hack for now due to terminals at start of line.  end seems ok.
                completedRouteCount++
        }

        return completedRouteCount +
                if (trainsByLine.values.flatten().size == transportersPerLine) 0 else 1
    }

    private fun getLineByTrain(id: UUID): String {
        var line = ""
        trainsByLine.forEach { (t, u) ->
            if (u.contains(id)) t.also { line = it }

        }
        return line
    }

    private fun getLineStations(line: Line): Set<Pair<String, String>> {
        val pairs = mutableSetOf<Pair<String, String>>()
        for (station in 0..line.stations.size - 2 step 1) {
            pairs.add(Pair("${line.name}:${line.stations[station]}", line.stations[station + 1]))
            pairs.add(Pair("${line.name}:${line.stations.reversed()[station]}", line.stations.reversed()[station + 1]))
        }
        return pairs.toSet()
    }

}
