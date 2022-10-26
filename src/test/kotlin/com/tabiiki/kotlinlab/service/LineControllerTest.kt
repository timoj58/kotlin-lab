package com.tabiiki.kotlinlab.service

import com.tabiiki.kotlinlab.model.Transport
import com.tabiiki.kotlinlab.util.LineBuilder
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.mockito.Mockito.atLeast
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import javax.naming.ConfigurationException

internal class LineControllerTest {

    private val line = LineBuilder().getLine()

    @Test
    fun `invalid start delay test`() {
        Assertions.assertThrows(ConfigurationException::class.java) {
            LineControllerImpl(
                100,
                mock(LineConductor::class.java)
            )
        }
    }

    @Test
    fun `start line and expect all trains to arrive at station B`() = runBlocking {
        val conductor = mock(LineConductor::class.java)
        `when`(conductor.getFirstTransportersToDispatch(listOf(line))).thenReturn(
            listOf(line.transporters[0], line.transporters[1])
        )

        `when`(conductor.getNextTransportersToDispatch(listOf(line))).thenReturn(
            listOf(line.transporters[2], line.transporters[3], line.transporters[4], line.transporters[5])
        )

        listOf(line.transporters[0], line.transporters[1])
            .forEach { `when`(conductor.isClear(it)).thenReturn(true) }

        val lineControllerService =
            LineControllerImpl(1000, conductor)

        val res = async { lineControllerService.start(listOf(line)) }
        delay(2000)

        verify(conductor, atLeast(2)).release(
            Transport(timeStep = 10, config = LineBuilder().transportConfig, line = LineBuilder().getLine())
        )

        verify(conductor, atLeast(4)).hold(
            Transport(timeStep = 10, config = LineBuilder().transportConfig, line = LineBuilder().getLine())
        )

        res.cancel()
    }


    @Test
    fun `start line and expect two trains to arrive at station B`() = runBlocking {
        val conductor = mock(LineConductor::class.java)
        `when`(conductor.getFirstTransportersToDispatch(listOf(line))).thenReturn(
            listOf(line.transporters[0], line.transporters[1])
        )
        val lineControllerService =
            LineControllerImpl(1000, conductor)

        val channel = Channel<Transport>()
        val res = async { lineControllerService.start(listOf(line)/*, channel*/) }
        delay(2000)

        verify(conductor, atLeast(2)).release(
            Transport(timeStep = 10, config = LineBuilder().transportConfig, line = LineBuilder().getLine())
        )

        res.cancel()
    }
}