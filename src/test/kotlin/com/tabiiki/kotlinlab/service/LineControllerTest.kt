package com.tabiiki.kotlinlab.service

import com.tabiiki.kotlinlab.configuration.TransportConfig
import com.tabiiki.kotlinlab.model.Transport
import com.tabiiki.kotlinlab.util.JourneyRepoImpl
import com.tabiiki.kotlinlab.util.LineBuilder
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*

internal class LineControllerTest {

    private val journeyRepoImpl = JourneyRepoImpl()
    private val line = LineBuilder().getLine(holdDelay = 15)

    @Test
    fun `start line and expect two trains to arrive at station B`() = runBlocking {
        val conductor = mock(LineConductor::class.java)
        val lineControllerService = LineControllerImpl(10000, listOf(line), conductor, journeyRepoImpl)

        val channel = Channel<Transport>()
        val res = async { lineControllerService.start(channel) }
        delay(100)

        verify(conductor, atLeast(2)).depart(
            Transport(LineBuilder().transportConfig)
        )
        res.cancelAndJoin()
    }


    @Test
    fun `test regulation that train is held before moving to next stop`() = runBlocking {
        val conductor = mock(LineConductor::class.java)
        val lineControllerService = LineControllerImpl(10000, listOf(line), conductor, journeyRepoImpl)

        val channel = Channel<Transport>()
        val res = async { lineControllerService.regulate(channel) }

        val transport = Transport(LineBuilder().transportConfig)
        transport.id = line.transporters.first().id
        channel.send(transport)
        delay(100)
        verify(conductor).hold(transport, 15)
        res.cancelAndJoin()

    }

}