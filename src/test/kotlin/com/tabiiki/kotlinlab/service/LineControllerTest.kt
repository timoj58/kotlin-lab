package com.tabiiki.kotlinlab.service

import com.tabiiki.kotlinlab.configuration.LineConfig
import com.tabiiki.kotlinlab.configuration.TransportConfig
import com.tabiiki.kotlinlab.model.Line
import com.tabiiki.kotlinlab.model.Transport
import com.tabiiki.kotlinlab.util.JourneyTimeRepoImpl
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*

internal class LineControllerTest {

    private val lineControllerUtilsImpl = JourneyTimeRepoImpl()

    private val transportConfig =
        TransportConfig(transportId = 1, capacity = 100, weight = 1000, topSpeed = 75, power = 100)

    private val line = Line(
        LineConfig(
            id = "1",
            name = "2",
            transportId = 1,
            transportCapacity = 8,
            stations = listOf("A", "B", "C"),
            depots = listOf("A", "C")
        ), listOf(transportConfig)
    )


    @Test
    fun `start line and expect two trains to arrive at station B`() = runBlocking {
        val conductor = mock(LineConductor::class.java)
        val lineControllerService = LineControllerImpl(10000, listOf(line), conductor, lineControllerUtilsImpl)

        val channel = Channel<Transport>()
        val res = async { lineControllerService.start(channel) }
        delay(100)

        verify(conductor, atLeast(2)).depart(
            Transport(TransportConfig(transportId = 1, capacity = 100, topSpeed = 75, power = 100, weight = 1000))
        )
        res.cancelAndJoin()
    }


    @Test
    fun `test regulation that train is held before moving to next stop`() = runBlocking {
        val conductor = mock(LineConductor::class.java)
        val lineControllerService = LineControllerImpl(10000, listOf(line), conductor, lineControllerUtilsImpl)

        val channel = Channel<Transport>()
        val res = async { lineControllerService.regulate(channel) }

        val transport = Transport(transportConfig)
        transport.id = line.transporters.first().id
        channel.send(transport)
        delay(100)
        verify(conductor).hold(transport, 15)
        res.cancelAndJoin()

    }

}