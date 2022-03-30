package com.tabiiki.kotlinlab.service

import com.tabiiki.kotlinlab.model.Status
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
    private val line = LineBuilder().getLine(holdDelay = 15)

    @BeforeEach
    fun `init`() {
        journeyRepoImpl.addJourneyTime(
            Pair(10, Pair("A", "B"))
        )
        journeyRepoImpl.addJourneyTime(
            Pair(10, Pair("B", "A"))
        )
        journeyRepoImpl.addJourneyTime(
            Pair(10, Pair("C", "B"))
        )
    }

    @Test
    fun `start line and expect all trains to arrive at station B`() = runBlocking {
        val conductor = mock(LineConductor::class.java)
        val lineControllerService = LineControllerImpl(100, listOf(line), conductor, journeyRepoImpl, mapOf())

        val channel = Channel<Transport>()
        val res = async { lineControllerService.start(channel) }
        delay(1000)

        verify(conductor, atLeast(line.transporters.size)).depart(
            Transport(timeStep = 10, config = LineBuilder().transportConfig, lineId = "1"),
            LineBuilder().lineStations
        )

        res.cancelAndJoin()
    }


    @Test
    fun `start line and expect two trains to arrive at station B`() = runBlocking {
        val conductor = mock(LineConductor::class.java)
        val lineControllerService = LineControllerImpl(100, listOf(line), conductor, journeyRepoImpl, mapOf())

        val channel = Channel<Transport>()
        val res = async { lineControllerService.start(channel) }
        delay(100)

        verify(conductor, atLeast(2)).depart(
            Transport(timeStep = 10, config = LineBuilder().transportConfig, lineId = "1"),
            LineBuilder().lineStations
        )

        res.cancelAndJoin()
    }


    @Test
    fun `test regulation that train is held before moving to next stop`() = runBlocking {
        val conductor = mock(LineConductor::class.java)
        val lineControllerService = LineControllerImpl(10000, listOf(line), conductor, journeyRepoImpl, mapOf())

        val channel = Channel<Transport>()
        val res = async { lineControllerService.regulate(channel) }

        val transport = Transport(config = LineBuilder().transportConfig, lineId = "1", timeStep = 1000)
        transport.id = line.transporters.first().id
        transport.status = Status.PLATFORM
        channel.send(transport)
        delay(100)
        verify(conductor).hold(transport, 15, LineBuilder().lineStations)
        res.cancelAndJoin()

    }


}