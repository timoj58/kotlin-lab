package com.tabiiki.kotlinlab.service

import com.tabiiki.kotlinlab.configuration.LinesConfig
import com.tabiiki.kotlinlab.configuration.StationsConfig
import com.tabiiki.kotlinlab.configuration.TransportConfig
import com.tabiiki.kotlinlab.configuration.TransportersConfig
import com.tabiiki.kotlinlab.configuration.adapter.LinesAdapter
import com.tabiiki.kotlinlab.configuration.adapter.TransportersAdapter
import com.tabiiki.kotlinlab.factory.LineFactory
import com.tabiiki.kotlinlab.factory.SignalFactoryV2
import com.tabiiki.kotlinlab.factory.SignalMessageV2
import com.tabiiki.kotlinlab.factory.SignalValue
import com.tabiiki.kotlinlab.factory.StationFactory
import com.tabiiki.kotlinlab.repo.JourneyRepo
import com.tabiiki.kotlinlab.repo.StationRepo
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

@Disabled
class PlatformServiceTest {

    private val stationsConfig = StationsConfig("src/main/resources/network/stations.csv")
    private val stationFactory = StationFactory(stationsConfig)
    private val stationRepo = StationRepo(stationFactory)
    private val journeyRepo = JourneyRepo()
    private val linesAdapter = LinesAdapter(
        listOf(
            "src/main/resources/network/overground/elizabeth.yml",
            "src/main/resources/network/tram/tram.yml",
            "src/main/resources/network/river/river.yml",
            "src/main/resources/network/underground/jubilee.yml",
            "src/main/resources/network/dockland/dlr.yml",
            "src/main/resources/network/underground/circle.yml"
        ),
        listOf(),
        listOf(),
        listOf(),
        listOf(),
        listOf()
    )

    //   private val lineRepo = LineRepo(stationRepo)
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

    private val lineFactory = LineFactory(
        timeStep = 3L,
        transportConfig = transportConfig,
        linesConfig = linesConfig
    )
    private val signalFactory = SignalFactoryV2()
    private val signalService = SignalServiceV2(3L)
    private val switchService = SwitchService(lineFactory)
    private val sectionService = SectionServiceV2(switchService)
    private val platformServiceV2 = PlatformServiceV2(
        sectionService = sectionService,
        signalService = signalService,
        lineService = LineService(lineFactory, stationRepo)
        //    lineFactory = lineFactory,
        //    lineRepo = lineRepo
    )

    @Test
    fun `init service and ensure all signals are green `() = runBlocking {
        val job = launch {
            platformServiceV2.init(
                commuterChannel = Channel()
            )
        }
        delay(1000)

        //   signalService.getPlatformSignals().forEach {
        //            key ->
        //        Assertions.assertThat(signalService.receive(key)?.signalValue).isEqualTo(SignalValue.GREEN)
        //   }

        job.cancel()
    }

    @Test
    fun `releasing a transporter sets the transporter in motion and sets the platform exit flag to RED`() = runBlocking {
        val job = launch {
            platformServiceV2.init(
                commuterChannel = Channel()
            )
        }
        delay(100)

        val transport = lineFactory.get(lineFactory.get().first()).transporters.first()
        Assertions.assertThat(transport.isStationary()).isTrue
        val releaseJob = launch {
            platformServiceV2.release(
                transport = transport
            )
        }
        delay(1000)
        Assertions.assertThat(transport.isStationary()).isFalse
        //  Assertions.assertThat(signalService.receive(Pair("${transport.platformKey().first}:${PlatformSignalType.EXIT}", transport.platformKey().second))?.signalValue).isEqualTo(SignalValue.RED)

        job.cancel()
        releaseJob.cancel()
    }

    @Test
    fun `releasing a transporter from a terminal sets the transporter in motion and sets the platform exit flag to RED`() = runBlocking {
        val job = launch {
            platformServiceV2.init(
                commuterChannel = Channel()
            )
        }
        delay(100)

        val transport = lineFactory.get("CIRCLE-2").transporters.first()
        Assertions.assertThat(transport.isStationary()).isTrue
        val releaseJob = launch {
            platformServiceV2.release(
                transport = transport
            )
        }
        delay(1000)
        Assertions.assertThat(transport.isStationary()).isFalse
        // Assertions.assertThat(signalService.receive(Pair("${transport.platformKey().first.substringBefore(":")}:${LineDirection.TERMINAL}", transport.platformKey().second))?.signalValue).isEqualTo(SignalValue.RED)

        job.cancel()
        releaseJob.cancel()
    }

    @Test
    fun `buffered release will add release a transporter if platform ENTRY is GREEN and platform EXIT is GREEN, and set platform entry to RED`() = runBlocking {
        val job = launch {
            platformServiceV2.init(
                commuterChannel = Channel()
            )
        }
        delay(100)

        val transport = lineFactory.get(lineFactory.get().first()).transporters.first()
        val transport2 = lineFactory.get(transport.line.id).transporters.first { it.section() == transport.section() && it.id != transport.id }
        val transport3 = lineFactory.get(transport.line.id).transporters.first { it.section() == transport.section() && !listOf(transport.id, transport2.id).contains(it.id) }
        val transport4 = lineFactory.get(transport.line.id).transporters.first { it.section() == transport.section() && !listOf(transport.id, transport2.id, transport3.id).contains(it.id) }
        Assertions.assertThat(transport.isStationary()).isTrue
        val releaseJob = launch {
            platformServiceV2.release(
                transport = transport
            )
        }
        delay(2000)
        Assertions.assertThat(transport.isStationary()).isFalse
        Assertions.assertThat(transport2.isStationary()).isTrue

        val releaseJob2 = launch {
            platformServiceV2.release(
                transporters = mutableListOf(transport2, transport3, transport4)
            )
        }
        // NOTE this tests the previous platform exit is now GREEN
        delay(2000)
        Assertions.assertThat(transport2.isStationary()).isFalse

        job.cancel()
        releaseJob.cancel()
        releaseJob2.cancel()
    }

    @Test
    fun `a transporter being held sets the platform entry signal to RED and is not released if the platform exit is RED`() = runBlocking {
        val job = launch {
            platformServiceV2.init(
                commuterChannel = Channel()
            )
        }
        delay(100)

        val transport = lineFactory.get(lineFactory.get().first()).transporters.first()

        // Assertions.assertThat(signalService.receive(Pair("${transport.platformKey().first}:${PlatformSignalType.ENTRY}", transport.platformKey().second))?.signalValue).isEqualTo(SignalValue.GREEN)
        val hold = launch { platformServiceV2.hold(transport) }
        delay(100)
        // Assertions.assertThat(signalService.receive(Pair("${transport.platformKey().first}:${PlatformSignalType.ENTRY}", transport.platformKey().second))?.signalValue).isEqualTo(SignalValue.RED)
        Assertions.assertThat(transport.isStationary()).isTrue

        delay(1000)
        Assertions.assertThat(transport.isStationary()).isFalse

        job.cancel()
        hold.cancel()
    }

    @Test
    fun `a transporter is released from hold after the minimum time and a GREEN signal to platform exit, and sets platform entry signal to GREEN`() = runBlocking {
        val job = launch {
            platformServiceV2.init(
                commuterChannel = Channel()
            )
        }
        delay(100)

        val transport = lineFactory.get(lineFactory.get().first()).transporters.first()

        signalService.send(
            key = Pair("${transport.platformKey().first}:${PlatformSignalType.EXIT}", transport.platformKey().second),
            message = SignalMessageV2(
                signalValue = SignalValue.RED,
                line = null
            )
        )
        val hold = launch { platformServiceV2.hold(transport) }
        delay(2000)
        Assertions.assertThat(transport.isStationary()).isTrue

        job.cancel()
        hold.cancel()
    }
}
