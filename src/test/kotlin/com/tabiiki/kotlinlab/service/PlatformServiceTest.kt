package com.tabiiki.kotlinlab.service

import com.tabiiki.kotlinlab.configuration.LinesConfig
import com.tabiiki.kotlinlab.configuration.StationsConfig
import com.tabiiki.kotlinlab.configuration.TransportConfig
import com.tabiiki.kotlinlab.configuration.TransportersConfig
import com.tabiiki.kotlinlab.configuration.adapter.LinesAdapter
import com.tabiiki.kotlinlab.configuration.adapter.TransportersAdapter
import com.tabiiki.kotlinlab.factory.LineFactory
import com.tabiiki.kotlinlab.factory.SignalFactory
import com.tabiiki.kotlinlab.factory.StationFactory
import com.tabiiki.kotlinlab.repo.JourneyRepoImpl
import com.tabiiki.kotlinlab.repo.LineRepoImpl
import com.tabiiki.kotlinlab.repo.StationRepoImpl
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test

internal class PlatformServiceTest {


    private val minimumHold = 45
    private val timeStep = 7L
    private val stationsConfig = StationsConfig("src/main/resources/network/stations.csv")
    private val linesAdapter = LinesAdapter(
        listOf(
            "src/main/resources/network/overground/elizabeth.yml",
            "src/main/resources/network/tram/tram.yml",
            "src/main/resources/network/river/river.yml",
            "src/main/resources/network/underground/jubilee.yml",
            "src/main/resources/network/dockland/dlr.yml"
        ),
        listOf(),
        listOf(),
        listOf(),
        listOf(),
        listOf()
    )
    private val linesConfig = LinesConfig(linesAdapter)
    private val transportersAdapter = TransportersAdapter(
        IntRange(1, 7).map {
            TransportConfig(
                transportId = it,
                capacity = 1000,
                weight = 1500,
                topSpeed = 20,
                power = 2300
            )
        }.toList()

    )
    private val transportConfig = TransportersConfig(transportersAdapter)

    private val lineFactory = LineFactory(timeStep, transportConfig, linesConfig)
    private val stationFactory = StationFactory(stationsConfig)

    private val journeyRepo = JourneyRepoImpl()
    private val stationRepo = StationRepoImpl(stationFactory)
    private val lineRepo = LineRepoImpl(stationRepo)
    private val signalFactory = SignalFactory(lineFactory)
    private val signalService = SignalServiceImpl(signalFactory)
    private val switchService = SwitchServiceImpl(lineFactory)

    private val sectionService = SectionServiceImpl(minimumHold, switchService, signalService, journeyRepo)
    private val platformService =
        PlatformServiceImpl(minimumHold, signalService, sectionService, lineRepo, stationRepo, lineFactory)

    private val lines = lineFactory.get().map { lineFactory.get(it) }

    private val stationService = StationServiceImpl(timeStep, signalService, stationFactory)

    private val jobs = mutableListOf<Job>()

    private suspend fun setup() = coroutineScope {
        jobs.clear()

        platformService.initCommuterChannel(Channel())
        lines.groupBy { it.name }.values.forEach { line ->
            val startJob = launch {
                platformService.initLines(line.map { it.name }.distinct().first(), line)
            }
            jobs.add(startJob)
        }
    }

    private suspend fun finish() {
        delay(100)
        jobs.forEach { it.cancel() }
    }

    @Test
    fun `testing terminal junction with incoming and outgoing traffic - Tram`() = runBlocking {
        /*
          173, 221, 127, 589 - TRAM01
          173, 221, 127, 589 - TRAM02
          173, 221, 127 - TRAM03

         */
        jobs.add(launch { setup() })
        delay(1000)

        //173 - POSITIVE
        val t1 = lines.first { it.id == "TRAM01" }.transporters.first().also { it.addSection(Pair("Tram:173", "221")) }
        //221 - POSITIVE
        val t2 = lines.first { it.id == "TRAM01" }.transporters.last().also { it.addSection(Pair("Tram:221", "127")) }
        //127 - NEGATIVE
        val t3 = lines.first { it.id == "TRAM03" }.transporters.first().also { it.addSection(Pair("Tram:221", "127")) }
        //589 - NEGATIVE
        val t4 = lines.first { it.id == "TRAM03" }.transporters.last().also { it.addSection(Pair("Tram:221", "127")) }

        //works, issue now how to recreate the issue, ie forcing transporters into sections to simulate it.

        listOf(/*t1, t2, t3, t4*/t3).forEach {
            println("releasing ${it.id} ${it.section()}")
            jobs.add(launch { platformService.signalAndDispatch(it) })
        }

        delay(1000)

        jobs.add(launch { platformService.signalAndDispatch(t4) })

        delay(20000)

        finish()
    }

    @Test
    fun `testing terminals in close proximity same direction - Elizabeth line`() = runBlocking {
        /*
           267, 271, 269 - ELIZ02
           267, 271, 269, 270 - ELIZ03
         */
        jobs.add(launch { setup() })
        delay(1000)
        finish()
    }

    @Test
    fun `testing problematic junction - River`() = runBlocking {
        /*
          670, 671, 673 - RIVER01
          670, 671, 672 - RIVER02
          671, 672 - RIVER03
          670, 671 - RIVER04
         */
        jobs.add(launch { setup() })
        delay(1000)
        finish()
    }

    @Test
    fun `testing problematic junction - DLR`() = runBlocking {
        /*
          628, 94, 275 DLR01
          435, 619, 94 DLR03
         */
        jobs.add(launch { setup() })
        delay(1000)
        finish()
    }

}
