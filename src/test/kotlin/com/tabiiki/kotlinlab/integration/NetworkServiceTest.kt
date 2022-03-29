package com.tabiiki.kotlinlab.integration

import com.tabiiki.kotlinlab.factory.LineFactory
import com.tabiiki.kotlinlab.service.NetworkService
import com.tabiiki.kotlinlab.service.StationMessage
import com.tabiiki.kotlinlab.service.StationsService
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import java.util.*

@ActiveProfiles("test")
@SpringBootTest
class NetworkServiceTest @Autowired constructor(
    val networkService: NetworkService
) {

    private val stationVisitedPerTrain = mutableMapOf<UUID, MutableSet<Pair<String, String>>>()
    private val stationsPerLine = listOf(
        Pair("26","598"), Pair("598", "26")
    )

    //TODO need to reduce hold delay.


    //@Disabled
    @Test
    fun `test all trains travel the line route`() = runBlocking()
    {

        val channel = Channel<StationMessage>()
        val res = async { networkService.start(channel) }
        val running = async { status(channel, res) }

    }

    //TODO HOLD delay needs to managed for test as well...
    private suspend fun status(channel: Channel<StationMessage>, job: Job) {
        do {
            val msg = channel.receive()
            if(msg.lineId == "CITY01")
               if(!stationVisitedPerTrain.containsKey(msg.transportId))
                   stationVisitedPerTrain[msg.transportId] = mutableSetOf()

               stationVisitedPerTrain[msg.transportId]?.add(msg.section)
        }while (!testSectionsVisited())

        job.cancelAndJoin()

        stationVisitedPerTrain.forEach { (t, u) ->
            println("train $t")
            u.forEach {section -> println("travelled ${section.first} to ${section.second}") }
        }

    }

    private fun testSectionsVisited(): Boolean {
        var test = true

        if(stationVisitedPerTrain.isEmpty()) return false

        stationVisitedPerTrain.forEach { (_, u) ->
            if(!u.containsAll(stationsPerLine))
                test = false
        }

        return test
    }




}