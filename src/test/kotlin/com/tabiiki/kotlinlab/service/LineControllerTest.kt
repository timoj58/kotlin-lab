package com.tabiiki.kotlinlab.service

import com.tabiiki.kotlinlab.model.Transport
import com.tabiiki.kotlinlab.util.LineBuilder
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.mockito.Mockito.atMost
import org.mockito.Mockito.atMostOnce
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

internal class LineControllerTest {

    private val line = LineBuilder().getLine()

    @Test
    fun `start line and expect all trains to arrive at station B`() = runBlocking {
        val conductor = mock(LineConductor::class.java)
        `when`(conductor.getTransportersToDispatch(listOf(line))).thenReturn(
            mutableListOf(
                line.transporters[0],
                line.transporters[1],
                line.transporters[2],
                line.transporters[3],
                line.transporters[4],
                line.transporters[5]
            )
        )

        val lineControllerService =
            LineController(conductor)

        val res = async { lineControllerService.start(listOf(line)) }
        delay(2000)

        verify(conductor, atMost(2)).release(
            Transport(timeStep = 10, config = LineBuilder().transportConfig, line = LineBuilder().getLine())
        )

        // ensure transporter only released once
        verify(conductor, atMostOnce()).release(
            line.transporters[0]
        )

        res.cancel()
    }

    @Test
    fun `start line and expect two trains to arrive at station B`() = runBlocking {
        val conductor = mock(LineConductor::class.java)
        `when`(conductor.getTransportersToDispatch(listOf(line))).thenReturn(
            mutableListOf(line.transporters[0], line.transporters[1])
        )
        val lineControllerService =
            LineController(conductor)

        val channel = Channel<Transport>()
        val res = async { lineControllerService.start(listOf(line)/*, channel*/) }
        delay(2000)

        verify(conductor, atMost(2)).release(
            Transport(timeStep = 10, config = LineBuilder().transportConfig, line = LineBuilder().getLine())
        )

        res.cancel()
    }
}
