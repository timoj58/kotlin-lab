package com.tabiiki.kotlinlab.service

import com.tabiiki.kotlinlab.factory.LineFactory
import com.tabiiki.kotlinlab.factory.SignalFactory
import com.tabiiki.kotlinlab.factory.SignalValue
import com.tabiiki.kotlinlab.model.Transport
import com.tabiiki.kotlinlab.repo.JourneyRepo
import com.tabiiki.kotlinlab.repo.LineRepoImpl
import com.tabiiki.kotlinlab.repo.StationRepo
import com.tabiiki.kotlinlab.util.LineBuilder
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock

internal class PlatformServiceTest {


    private val lineFactory = mock(LineFactory::class.java)
    private var signalFactory: SignalFactory? = null
    private var signalService: SignalServiceImpl? = null
    private val stationRepo = mock(StationRepo::class.java)

    private val transport = Transport(
        config = LineBuilder().transportConfig,
        timeStep = 10,
        line = LineBuilder().getLine()
    ).also {
        it.addSection(Pair("1:A", "B"))
    }

    private val transport2 = Transport(
        config = LineBuilder().transportConfig,
        timeStep = 10,
        line = LineBuilder().getLine()
    ).also {
        it.addSection(Pair("1:A", "B"))
    }

    private val lines = listOf(LineBuilder().getLine().also {
        it.transporters[0].id = transport.id
        it.transporters[1].id = transport2.id
    })

    @BeforeEach
    fun init() {
        `when`(lineFactory.get()).thenReturn(listOf("1"))
        `when`(lineFactory.get("1")).thenReturn(LineBuilder().getLine())
        `when`(lineFactory.timeStep).thenReturn(10L)

        signalFactory = SignalFactory(lineFactory)
        signalService = SignalServiceImpl(signalFactory!!)

        `when`(stationRepo.get("A")).thenReturn(LineBuilder().stations[0])
        `when`(stationRepo.get("B")).thenReturn(LineBuilder().stations[1])
        `when`(stationRepo.get("C")).thenReturn(LineBuilder().stations[2])

        `when`(
            stationRepo.getNextStationOnLine(
                listOf("A", "B", "C"),
                Pair("A", "B")
            )
        ).thenReturn(LineBuilder().stations[2])
        `when`(
            stationRepo.getNextStationOnLine(
                listOf("A", "B", "C"),
                Pair("B", "C")
            )
        ).thenReturn(LineBuilder().stations[1])
        `when`(
            stationRepo.getNextStationOnLine(
                listOf("A", "B", "C"),
                Pair("C", "B")
            )
        ).thenReturn(LineBuilder().stations[0])
        `when`(
            stationRepo.getNextStationOnLine(
                listOf("A", "B", "C"),
                Pair("B", "A")
            )
        ).thenReturn(LineBuilder().stations[1])

        `when`(
            stationRepo.getPreviousStationOnLine(
                listOf("A", "B", "C"),
                Pair("1:A", "B")
            )
        ).thenReturn(LineBuilder().stations[1])

        `when`(
            stationRepo.getPreviousStationOnLine(
                listOf("A", "B", "C"),
                Pair("1:B", "C")
            )
        ).thenReturn(LineBuilder().stations[0])

        `when`(
            stationRepo.getPreviousStationOnLine(
                listOf("A", "B", "C"),
                Pair("1:C", "B")
            )
        ).thenReturn(LineBuilder().stations[1])

        `when`(
            stationRepo.getPreviousStationOnLine(
                listOf("A", "B", "C"),
                Pair("1:B", "A")
            )
        ).thenReturn(LineBuilder().stations[2])

    }

    @Test
    fun `train is first train added to section, so will be given a green light`() = runBlocking {
        val sectionService = SectionServiceImpl(45, signalService!!, mock(JourneyRepo::class.java))
        val platformService =
            PlatformServiceImpl(45, signalService!!, sectionService, LineRepoImpl(stationRepo), stationRepo)

        val start = launch { platformService.start(LineBuilder().getLine().name, lines) }
        val job = launch { platformService.release(transport) }

        do {
            delay(100)
        } while (transport.isStationary())

        assertThat(transport.isStationary()).isEqualTo(false)

        job.cancelAndJoin()
        start.cancelAndJoin()
    }

    @Test
    fun `train is second train added to section, so will be given a red light`() = runBlocking {
        val sectionService = SectionServiceImpl(45, signalService!!, mock(JourneyRepo::class.java))
        val platformService =
            PlatformServiceImpl(45, signalService!!, sectionService, LineRepoImpl(stationRepo), stationRepo)

        val start = launch { platformService.start(LineBuilder().getLine().name, lines) }
        delay(200)

        val job = launch { platformService.release(transport) }
        delay(100)
        val job2 = launch { platformService.hold(transport2) }

        do {
            delay(100)
        } while (!transport.isStationary())

        assertThat(transport.isStationary() != transport2.isStationary()).isEqualTo(true)

        job.cancelAndJoin()
        job2.cancelAndJoin()
        start.cancelAndJoin()
    }

    @Test
    fun `train is second train added to section, so will be given a red light, and then get a green light`() =
        runBlocking {
            val sectionService = SectionServiceImpl(45, signalService!!, mock(JourneyRepo::class.java))
            val platformService =
                PlatformServiceImpl(45, signalService!!, sectionService, LineRepoImpl(stationRepo), stationRepo)

            val start = launch { platformService.start(LineBuilder().getLine().name, lines) }
            delay(200)
            val job = launch { platformService.release(transport) }
            delay(1000)
            val job2 = launch { platformService.hold(transport2) }

            do {
                delay(100)
            } while (!transport.atPlatform())

            assertThat(transport.atPlatform()).isEqualTo(true)

            do {
                delay(100)
            } while (transport2.isStationary())

            assertThat(transport2.isStationary()).isEqualTo(false)

            job.cancelAndJoin()
            job2.cancelAndJoin()
            start.cancelAndJoin()
        }

    //TODO fix this.
    @Disabled
    @Test
    fun `test that section to platform is set Red when a train is held at platform`() = runBlocking {
        val journeyRepo = mock(JourneyRepo::class.java)
        val sectionService = SectionServiceImpl(45, signalService!!, journeyRepo)
        val platformService =
            PlatformServiceImpl(45, signalService!!, sectionService, LineRepoImpl(stationRepo), stationRepo)

        `when`(journeyRepo.getJourneyTime(Pair("1:A", "B"))).thenReturn(70)

        val start = launch { platformService.start(LineBuilder().getLine().name, lines) }
        delay(200)
        val job = launch { platformService.release(transport) }
        delay(200)
        val channelToListenTo = signalService!!.getChannel(Pair("1:A", "B"))

        var signalValue: SignalValue?
        do {
            delay(transport.timeStep)
            signalValue = channelToListenTo!!.receive().signalValue

        } while (transport.section() != Pair("1:C", "B") && signalValue != SignalValue.RED)

        assertThat(signalValue).isEqualTo(SignalValue.RED)

        job.cancelAndJoin()
        start.cancelAndJoin()

    }

}