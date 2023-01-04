package com.tabiiki.kotlinlab.monitor

import com.tabiiki.kotlinlab.configuration.LinesConfig
import com.tabiiki.kotlinlab.configuration.StationsConfig
import com.tabiiki.kotlinlab.configuration.TransportConfig
import com.tabiiki.kotlinlab.configuration.TransportersConfig
import com.tabiiki.kotlinlab.configuration.adapter.LinesAdapter
import com.tabiiki.kotlinlab.configuration.adapter.TransportersAdapter
import com.tabiiki.kotlinlab.factory.LineFactory
import com.tabiiki.kotlinlab.factory.SignalFactory
import com.tabiiki.kotlinlab.factory.SignalMessage
import com.tabiiki.kotlinlab.factory.SignalValue
import com.tabiiki.kotlinlab.factory.StationFactory
import com.tabiiki.kotlinlab.model.Transport
import com.tabiiki.kotlinlab.repo.JourneyRepoImpl
import com.tabiiki.kotlinlab.repo.LineRepoImpl
import com.tabiiki.kotlinlab.repo.StationRepoImpl
import com.tabiiki.kotlinlab.service.SectionServiceImpl
import com.tabiiki.kotlinlab.service.SignalServiceImpl
import com.tabiiki.kotlinlab.service.SwitchServiceImpl
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.util.UUID

@Disabled
//not really a test class yet, needs more work but useful to break into simulation when debugging lines
class PlatformMonitorTest {

    private val minimumHold = 45
    private val timeStep = 7L

    private val linesAdapter = LinesAdapter(
        listOf("src/main/resources/network/river/river.yml"),
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
            ),
            TransportConfig(
                transportId = 7,
                capacity = 1000,
                weight = 1500,
                topSpeed = 20,
                power = 2300
            )
        )
    )
    private val transportConfig = TransportersConfig(transportersAdapter)
    private val lineFactory = LineFactory(timeStep, transportConfig, linesConfig)
    private val switchService = SwitchServiceImpl(lineFactory)
    private val signalFactory = SignalFactory(lineFactory)

    private val signalService = SignalServiceImpl(signalFactory)

    private val journeyRepo = JourneyRepoImpl()
    private val sectionService = SectionServiceImpl(
        minimumHold = minimumHold,
        journeyRepo = journeyRepo,
        switchService = switchService,
        signalService = signalService
    )

    private val stationsConfig = StationsConfig("src/main/resources/network/stations.csv")
    private val stationFactory = StationFactory(stationsConfig)
    private val stationRepo = StationRepoImpl(stationFactory)
    private val lineRepo = LineRepoImpl(stationRepo = stationRepo)

    private val lines = lineFactory.get().map { lineFactory.get(it) }

    private val platformMonitor = PlatformMonitor(
        sectionService = sectionService,
        signalService = signalService,
        lineRepo = lineRepo
    )

    @BeforeEach
    fun setup() {
        signalService.getSectionSignals().forEach { sectionService.initQueues(it) }
        signalService.getPlatformSignals().forEach { platformMonitor.init(it) }
    }

    @Test
    fun `platform RED ` () = runBlocking {

        val job = launch { initMonitor() }

        //no transporters for this.
        delay(1000)
        //this is the issue...has two stations, should be one.
        sendSignal(key = Pair("River:NEGATIVE","River:672"), signalValue = SignalValue.RED)
        platformMonitor.getHoldChannel(lines.first().transporters.first())
        //now test that the sections have received a red (670,671) (672,671)
        //mainly as its not a terminal....which is the issue.  tricky one.  (fix is dont include length of 2).

        /*
           so test wise...use transporters, to verify they receive the correct signals.
           ie motionless transporters, therefore override current transporter with test fakes (not mocks).

         */

        delay(1000)
        job.cancel()
    }

    @Test
    fun `platform GREEN ` () = runBlocking {

        val job = launch { initMonitor() }
        delay(1000)

        sendSignal(key = Pair("River:NEGATIVE","River:672"), signalValue = SignalValue.RED)
        delay(1000)

        sendSignal(key = Pair("River:NEGATIVE","River:672"), signalValue = SignalValue.GREEN)
        //test that the sections have received a green

        delay(10000)
        job.cancel()
    }

    private suspend fun sendSignal(key: Pair<String, String>, signalValue: SignalValue) {
        signalService.send(
            key, SignalMessage(
                signalValue = signalValue,
                id = UUID.randomUUID(),
                key = key,
                line = "RIVER01",
                commuterChannel = null,
            ))
    }

    private suspend fun initMonitor() = coroutineScope {
        lineRepo.addLineDetails("River", lines)
        launch { sectionService.init("River") }

        //init call.
        platformMonitor.getPlatformKeys().filter { it.first.contains("River") }.forEach {
            launch { init(it) }
            launch { platformMonitor.monitorPlatformHold(it) { t -> launch { hold(t) } } }
        }

    }

    private fun hold(transport: Transport) {
        println("holding $transport")
    }

    private suspend fun init(key: Pair<String, String>) = coroutineScope {
        launch { signalService.init(key) }
        launch { platformMonitor.monitorPlatform(key) }
    }

}