package com.tabiiki.kotlinlab.integration

import com.tabiiki.kotlinlab.factory.LineFactory
import com.tabiiki.kotlinlab.service.NetworkService
import com.tabiiki.kotlinlab.service.StationMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import java.util.*

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
            sectionsByLine[id] = getLineStations(line.stations)
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
        //need to time this out.  at some point.  TODO
        do {
            val msg = channel.receive()
            if (!trainsByLine.containsKey(msg.lineId))
                trainsByLine[msg.lineId] = mutableSetOf()
            if (!stationVisitedPerTrain.containsKey(msg.transportId))
                stationVisitedPerTrain[msg.transportId] = mutableSetOf()

            trainsByLine[msg.lineId]?.add(msg.transportId)
            stationVisitedPerTrain[msg.transportId]?.add(msg.section)
        } while (!testSectionsVisited())

        job.cancelAndJoin()

        // Assertions.assertThat(trainsByLine.values.flatten().size).isEqualTo(transportersPerLine)

        stationVisitedPerTrain.forEach { (t, u) ->
            println("train $t")
            u.forEach { section -> println("travelled ${section.first} to ${section.second}") }
        }

    }

    private fun testSectionsVisited(): Boolean {
        var test = true

        if (stationVisitedPerTrain.isEmpty()) return false

        stationVisitedPerTrain.forEach { (k, u) ->
            if (!u.containsAll(sectionsByLine[getLineByTrain(k)]!!.toList()))
                test = false
        }

        return test && trainsByLine.values.flatten().size == transportersPerLine
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
        for (station in 0..stations.size - 2 step 2) {
            pairs.add(Pair(stations[station], stations[station + 1]))
            pairs.add(Pair(stations.reversed()[station], stations.reversed()[station + 1]))
        }
        return pairs.toSet()
    }


}