package com.tabiiki.kotlinlab.service

import com.tabiiki.kotlinlab.factory.LineFactory
import com.tabiiki.kotlinlab.factory.SignalFactory
import com.tabiiki.kotlinlab.model.Line
import com.tabiiki.kotlinlab.model.Station
import com.tabiiki.kotlinlab.model.Transport
import com.tabiiki.kotlinlab.repo.JourneyRepo
import com.tabiiki.kotlinlab.repo.StationRepo
import com.tabiiki.kotlinlab.util.LineBuilder
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock

internal class LineSectionServiceTest {

    private val lineFactory = mock(LineFactory::class.java)
    private var signalFactory: SignalFactory? = null
    private var signalService: SignalServiceImpl? = null
    private val stationRepo = mock(StationRepo::class.java)
    private val journeyRepo = mock(JourneyRepo::class.java)

    private val transport = Transport(
        config = LineBuilder().transportConfig,
        timeStep = 10,
        line = LineBuilder().getLine()
    ).also {
        it.addSection(Pair("A", "B"))
    }

    private val transport2 = Transport(
        config = LineBuilder().transportConfig,
        timeStep = 10,
        line = LineBuilder().getLine()
    ).also {
        it.addSection(Pair("A", "B"))
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

        `when`(stationRepo.getNextStationOnLine(listOf("A", "B", "C"), Pair("A", "B"))).thenReturn(LineBuilder().stations[0])
        `when`(stationRepo.getNextStationOnLine(listOf("A", "B", "C"), Pair("B", "C"))).thenReturn(LineBuilder().stations[0])
        `when`(stationRepo.getNextStationOnLine(listOf("A", "B", "C"), Pair("C", "B"))).thenReturn(LineBuilder().stations[0])
        `when`(stationRepo.getNextStationOnLine(listOf("A", "B", "C"), Pair("B", "A"))).thenReturn(LineBuilder().stations[0])

    }

    @Test
    fun `train is first train added to section, so will be given a green light`() = runBlocking {
        val lineSectionService = LineSectionServiceImpl(45, signalService!!, journeyRepo, stationRepo)

        val job2 = launch { lineSectionService.start(LineBuilder().getLine().name, lines) }
        val job = launch { lineSectionService.release(transport) }

        do {
            delay(100)
        } while (transport.isStationary())

        assertThat(transport.isStationary()).isEqualTo(false)

        job2.cancelAndJoin()
        job.cancelAndJoin()
    }

    @Test
    fun `train is second train added to section, so will be given a red light`() = runBlocking {
        val lineSectionService = LineSectionServiceImpl(45, signalService!!,journeyRepo, stationRepo)

        val job3 = launch { lineSectionService.start(LineBuilder().getLine().name, lines) }
        delay(200)

        val job = launch { lineSectionService.release(transport) }
        delay(100)
        val job2 = launch { lineSectionService.release(transport2) }

        do {
            delay(100)
        } while (transport.isStationary() == transport2.isStationary())

        assertThat(transport.isStationary() != transport2.isStationary()).isEqualTo(true)

        job.cancelAndJoin()
        job2.cancelAndJoin()
        job3.cancelAndJoin()
    }

    @Test
    fun `train is second train added to section, so will be given a red light, and then get a green light once section clear`() =
        runBlocking {
            val lineSectionService = LineSectionServiceImpl(45, signalService!!,journeyRepo, stationRepo)

            val job3 = launch { lineSectionService.start(LineBuilder().getLine().name, lines) }
            val job = launch { lineSectionService.release(transport) }
            delay(100)
            val job2 = launch { lineSectionService.release(transport2) }

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
            job3.cancelAndJoin()
        }

}