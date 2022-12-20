package com.tabiiki.kotlinlab.service

import com.tabiiki.kotlinlab.configuration.LinesConfig
import com.tabiiki.kotlinlab.configuration.StationsConfig
import com.tabiiki.kotlinlab.configuration.TransportConfig
import com.tabiiki.kotlinlab.configuration.TransportersConfig
import com.tabiiki.kotlinlab.configuration.adapter.LinesAdapter
import com.tabiiki.kotlinlab.configuration.adapter.TransportersAdapter
import com.tabiiki.kotlinlab.factory.AvailableRoute
import com.tabiiki.kotlinlab.factory.LineFactory
import com.tabiiki.kotlinlab.factory.SignalFactory
import com.tabiiki.kotlinlab.factory.StationFactory
import com.tabiiki.kotlinlab.model.Commuter
import com.tabiiki.kotlinlab.model.Status
import com.tabiiki.kotlinlab.repo.JourneyRepoImpl
import com.tabiiki.kotlinlab.repo.LineRepoImpl
import com.tabiiki.kotlinlab.repo.StationRepoImpl
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

internal class PlatformServiceTest {

    private val minimumHold = 45
    private val timeStep = 5L
    private val stationsConfig = StationsConfig("src/main/resources/network/stations.csv")
    private val linesAdapter = LinesAdapter(
        listOf("src/test/resources/network/test-line2.yml"),
        listOf(),
        listOf(),
        listOf(),
        listOf(),
        listOf()
    )
    private val linesConfig = LinesConfig(linesAdapter)
    private val transportersAdapter = TransportersAdapter(
        listOf(
            TransportConfig(
                transportId = 1,
                capacity = 1000,
                weight = 1500,
                topSpeed = 20,
                power = 2300
            )
        )
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
    private val platformService = PlatformServiceImpl(minimumHold, signalService, sectionService, lineRepo, stationRepo)

    private val lines = lineFactory.get().map { lineFactory.get(it) }

    private val stationService = StationServiceImpl(timeStep, signalService, stationFactory)

    @Test
    fun `platform & section service test`() = runBlocking {


        val jobs = mutableListOf<Job>()
        val globalCommuterChannel = Channel<Commuter>()

        jobs.add(
            launch { stationService.start(Channel(), globalCommuterChannel, linesConfig.lines.first().name) }
        )

        val routeEnquiryChannel = Channel<RouteEnquiry>()

        val commuter = Commuter(
            commute = Pair(Pair("26", "94"), routeEnquiryChannel),
            timeStep = 10,
        ) {
           jobs.add( launch { globalCommuterChannel.send(it) } )
        }

        val init = launch { commuter.initJourney() }

        jobs.add(init)

        val enquiry = launch { commuter.channel.send(
            AvailableRoute(
                route = mutableListOf(Pair("29", "94"))))
        }

        jobs.add(enquiry)

        val tracker: ConcurrentHashMap<UUID, MutableSet<Pair<String, String>>> = ConcurrentHashMap()
        val lineData = mutableListOf<Triple<String, Int, List<UUID>>>()

        lines.forEach { line ->
            lineData.add(Triple(line.id, (line.stations.size * 2) - 2, line.transporters.map { it.id }))
        }

        lines.flatMap { it.transporters }.forEach {
            tracker[it.id] = mutableSetOf()
        }
        //INIT start
        lines.groupBy { it.name }.values.forEach { line ->
            val startJob = launch {
                platformService.init(line.map { it.name }.distinct().first(), line, globalCommuterChannel)
            }
            jobs.add(startJob)
        }

        lines.map { it.transporters }.flatten().groupBy { it.section() }.values.flatten()
            .distinctBy { it.section().first }.forEach {
                tracker[it.id]!!.add(it.section())
                val job = launch { platformService.release(it) }
                jobs.add(job)
            }

        val releaseTime = System.currentTimeMillis()

        do {
            delay(1000) //minimum start delay
            lines.map { it.transporters }.flatten().filter { it.status == Status.DEPOT }
                .groupBy { it.section() }.values.flatten().distinctBy { it.section().first }
                .forEach {
                    if (platformService.isClear(it) && platformService.canLaunch(it)) {
                        tracker[it.id]!!.add(it.section())
                        val job = launch {
                            platformService.hold(it)
                        }
                        jobs.add(job)
                    }
                }
        } while (lines.flatMap { it.transporters }.any { it.status == Status.DEPOT }
            && System.currentTimeMillis() < releaseTime + (1000 * 60))
        //INIT end
        assertThat(lines.flatMap { it.transporters }.any { it.status == Status.DEPOT }).isEqualTo(false)
        //SIMULATE start
        val startTime = System.currentTimeMillis()

        do {
            lines.flatMap { it.transporters }.forEach {
                if (it.atPlatform())
                    tracker[it.id]!!.add(it.section())
            }
            delay(timeStep)
        } while (!completed(lineData, tracker)
            && System.currentTimeMillis() < startTime + (1000 * 60)
        )
        //SIMULATE end

        //TEST
        tracker.forEach { (t, u) ->
            println("$t: ${u.size} vs ${lineData.first { it.third.contains(t) }.second}")
        }

        assertThat(completed(lineData, tracker)).isEqualTo(true)

        jobs.forEach {
            it.cancel()
        }
    }

    private fun completed(
        lineData: List<Triple<String, Int, List<UUID>>>,
        tracker: ConcurrentHashMap<UUID, MutableSet<Pair<String, String>>>
    ): Boolean {
        val results = mutableListOf<Boolean>()
        tracker.forEach { (t, u) ->
            val sections = lineData.first { it.third.contains(t) }.second
            results.add(sections >= u.size) //todo due to terminal end points being added.
        }
        return results.all { it }
    }
}