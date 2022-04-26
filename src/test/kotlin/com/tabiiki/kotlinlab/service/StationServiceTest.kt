package com.tabiiki.kotlinlab.service


import com.tabiiki.kotlinlab.configuration.TransportConfig
import com.tabiiki.kotlinlab.factory.SignalValue
import com.tabiiki.kotlinlab.model.Transport
import com.tabiiki.kotlinlab.repo.StationRepo
import com.tabiiki.kotlinlab.util.LineBuilder
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock

@Disabled
internal class StationServiceTest {

    //TODO need to fix all of this

    private val stationRepo = mock(StationRepo::class.java)

    @BeforeEach
    fun `init`() {
        `when`(stationRepo.get()).thenReturn(LineBuilder().stations)
    }

    @Test
    fun `train departing station B`() = runBlocking {
        val stationService = StationServiceImpl(stationRepo)

        val channel = Channel<Transport>()
        val channel2 = Channel<StationMessage>()

        val job = async { stationService.monitor("B", channel, channel2) }
        val transport =
            Transport(
                config = TransportConfig(transportId = 1, capacity = 1),
                line = LineBuilder().getLine(),
                timeStep = 1000
            )
        transport.addSection(Pair("B", "A"))
        val job2 = async { channel.send(transport) }

        delay(100)

        job.cancelAndJoin()
        job2.cancelAndJoin()

    }

    @Test
    fun `train arriving station B`() = runBlocking {
        val stationService = StationServiceImpl(stationRepo)

        val channel = Channel<Transport>()
        val channel2 = Channel<StationMessage>()

        val job = async { stationService.monitor("B", channel, channel2) }
        val transport =
            Transport(
                config = TransportConfig(transportId = 1, capacity = 1),
                line = LineBuilder().getLine(),
                timeStep = 1000
            )
        transport.addSection(Pair("A", "B"))
        transport.release(
            LineInstructions(
                LineBuilder().stations[0],
                LineBuilder().stations[1],
                LineBuilder().stations[2],
                LineDirection.POSITIVE
            )
        )
        val channel3 = Channel<SignalValue>()
        val depart = async { transport.signal(channel3) }
        channel3.send(SignalValue.GREEN)
        val job2 = async { channel.send(transport) }

        delay(200)

        depart.cancelAndJoin()
        job.cancelAndJoin()
        job2.cancelAndJoin()

    }

}