package com.tabiiki.kotlinlab.service

import com.tabiiki.kotlinlab.model.Transport
import com.tabiiki.kotlinlab.repo.JourneyRepoImpl
import com.tabiiki.kotlinlab.util.LineBuilder
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*

internal class LineControllerTest {

    private val journeyRepoImpl = JourneyRepoImpl()
    private val line = LineBuilder().getLine()

    @BeforeEach
    fun `init`() {
        journeyRepoImpl.addJourneyTime(
            Pair(Pair("A", "B"), 10)
        )
        journeyRepoImpl.addJourneyTime(
            Pair(Pair("B", "A"), 10)
        )
        journeyRepoImpl.addJourneyTime(
            Pair(Pair("C", "B"), 10)
        )

    }

    @Test
    fun `start line and expect all trains to arrive at station B`() = runBlocking {
        val conductor = mock(PlatformConductor::class.java)
        `when`(conductor.getFirstTransportersToDispatch(listOf(line))).thenReturn(
            listOf(line.transporters[0], line.transporters[1])
        )

        `when`(conductor.getNextTransportersToDispatch(listOf(line))).thenReturn(
            listOf(line.transporters[2], line.transporters[3], line.transporters[4], line.transporters[5])
        )

        val lineControllerService =
            LineControllerImpl(100, listOf(line), conductor, journeyRepoImpl, mapOf())

        val channel = Channel<Transport>()
        val res = async { lineControllerService.start(channel) }
        delay(1000)

        verify(conductor, atLeast(line.transporters.size)).release(
            Transport(timeStep = 10, config = LineBuilder().transportConfig, line = LineBuilder().getLine()),
            LineBuilder().lineStations
        )

        res.cancelAndJoin()
    }


    @Test
    fun `start line and expect two trains to arrive at station B`() = runBlocking {
        val conductor = mock(PlatformConductor::class.java)
        `when`(conductor.getFirstTransportersToDispatch(listOf(line))).thenReturn(
            listOf(line.transporters[0], line.transporters[1])
        )
        val lineControllerService =
            LineControllerImpl(100, listOf(line), conductor, journeyRepoImpl, mapOf())

        val channel = Channel<Transport>()
        val res = async { lineControllerService.start(channel) }
        delay(100)

        verify(conductor, atLeast(2)).release(
            Transport(timeStep = 10, config = LineBuilder().transportConfig, line = LineBuilder().getLine()),
            LineBuilder().lineStations
        )

        res.cancelAndJoin()
    }
}