package com.tabiiki.kotlinlab.service

import com.tabiiki.kotlinlab.factory.LineFactory
import com.tabiiki.kotlinlab.factory.SignalFactory
import com.tabiiki.kotlinlab.model.Transport
import com.tabiiki.kotlinlab.util.LineBuilder
import kotlinx.coroutines.async
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

    private val instructions =
        LineInstructions(LineBuilder().stations[0], LineBuilder().stations[1], LineBuilder().stations[2], LineDirection.POSITIVE);

    private val transport = Transport(
        config = LineBuilder().transportConfig,
        timeStep = 10,
        lineId = "1"
    ).also {
        it.section = Pair("A", "B")
    }

    private val transport2 = Transport(
        config = LineBuilder().transportConfig,
        timeStep = 10,
        lineId = "1"
    ).also {
        it.section = Pair("A", "B")
    }

    @BeforeEach
    fun init(){
        `when`(lineFactory.get()).thenReturn(listOf("1"))
        `when`(lineFactory.get("1")).thenReturn(LineBuilder().getLine())
        `when`(lineFactory.timeStep).thenReturn(10L)

        signalFactory = SignalFactory(lineFactory)
        signalService = SignalServiceImpl(signalFactory!!)
    }

    @Test
    fun `train is first train added to section, so will be given a green light`() = runBlocking {
        val lineSectionService = LineSectionServiceImpl(signalService!!)

        val job2 = launch { lineSectionService.start() }
        delay(100)

        val job = async {  lineSectionService.release(transport, instructions)}
        delay(1000)

        assertThat(transport.isStationary()).isEqualTo(false)

        job2.cancelAndJoin()
        job.cancelAndJoin()
      }

    @Test
    fun `train is second train added to section, so will be given a red light`() = runBlocking{
        val lineSectionService = LineSectionServiceImpl(signalService!!)

        val job3 = async { lineSectionService.start() }
        delay(100)

        val job = async { lineSectionService.release(transport, instructions) }
        val job2 = async { lineSectionService.release(transport2, instructions) }

        delay(1000)

        assertThat(transport.isStationary() != transport2.isStationary()).isEqualTo(true)

        job.cancelAndJoin()
        job2.cancelAndJoin()
        job3.cancelAndJoin()
    }

    @Test
    fun `train is second train added to section, so will be given a red light, and then get a green light once section clear`() = runBlocking{
        val lineSectionService = LineSectionServiceImpl(signalService!!)

        val job3 = async { lineSectionService.start() }

        val job = async { lineSectionService.release(transport, instructions) }
        val job2 = async { lineSectionService.release(transport2, instructions) }

        delay(11000)

        assertThat(transport.atPlatform()).isEqualTo(true)
        assertThat(transport2.isStationary()).isEqualTo(false)

        job.cancelAndJoin()
        job2.cancelAndJoin()
        job3.cancelAndJoin()
    }

}