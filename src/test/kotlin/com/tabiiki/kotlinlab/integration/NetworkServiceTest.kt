package com.tabiiki.kotlinlab.integration

import com.tabiiki.kotlinlab.factory.LineFactory
import com.tabiiki.kotlinlab.service.MessageType
import com.tabiiki.kotlinlab.service.NetworkService
import com.tabiiki.kotlinlab.service.StationMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import java.util.*

@Disabled
@ActiveProfiles("test")
@SpringBootTest
class NetworkServiceTest @Autowired constructor(
    val networkService: NetworkService,
    private val lineFactory: LineFactory
) {

    private val stationVisitedPerTrain = mutableMapOf<UUID, MutableSet<Pair<String, String>>>()
    private val trainsByLine = mutableMapOf<String, MutableSet<UUID>>()
    private val sectionsByLine = mutableMapOf<String, Set<Pair<String, String>>>()
    private var transportersPerLine = 0

    init {
        lineFactory.get().forEach { id ->
            val line = lineFactory.get(id)
            sectionsByLine[line.name] = getLineStations(line.stations)
            transportersPerLine += line.transporters.size
        }
    }

    @Test
    fun `test all trains travel the line route`() = runBlocking()
    {
        val channel = Channel<StationMessage>()
        val res = async { networkService.start(channel) }
        val running = async { status(channel, res) }
    }

    private suspend fun status(channel: Channel<StationMessage>, job: Job) {
        val startTime = System.currentTimeMillis()
        do {
            val msg = channel.receive()
            if (!trainsByLine.containsKey(msg.line))
                trainsByLine[msg.line] = mutableSetOf()
            if (!stationVisitedPerTrain.containsKey(msg.transportId))
                stationVisitedPerTrain[msg.transportId] = mutableSetOf()

            trainsByLine[msg.line]?.add(msg.transportId)
            if(msg.type == MessageType.ARRIVE) stationVisitedPerTrain[msg.transportId]?.add(msg.section)
        } while (/*testSectionsVisited() != transportersPerLine &&*/ startTime + (1000 * 60 * 1) > System.currentTimeMillis())

        job.cancelAndJoin()
        assert()
    }

    private fun assert() {
        println("total trains: $transportersPerLine, trains running: ${stationVisitedPerTrain.keys.size}  and stations visited ${stationVisitedPerTrain.values.flatten().size}")
        val count = testSectionsVisited()
        println("completed journeys count: $count")
        assertThat(count).isEqualTo(transportersPerLine)
        stationVisitedPerTrain.values.flatten()
            .forEach {
                if (it.second == it.first) println("incorrect route $it")
                assertThat(it.second == it.first).isEqualTo(false)
            }
    }

    private fun testSectionsVisited(): Int {
        var completedRouteCount = 0

        if (stationVisitedPerTrain.isEmpty()) return 1

        stationVisitedPerTrain.forEach { (k, u) ->
            if (u.containsAll(sectionsByLine[getLineByTrain(k)]!!.toList()))
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

    private fun getLineStations(stations: List<String>): Set<Pair<String, String>> {
        var pairs = mutableSetOf<Pair<String, String>>()
        for (station in 0..stations.size - 2 step 1) {
            pairs.add(Pair(stations[station], stations[station + 1]))
            pairs.add(Pair(stations.reversed()[station], stations.reversed()[station + 1]))
        }
        return pairs.toSet()
    }


}