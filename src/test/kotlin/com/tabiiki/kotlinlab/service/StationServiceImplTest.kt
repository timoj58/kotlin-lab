package com.tabiiki.kotlinlab.service

import com.tabiiki.kotlinlab.factory.SignalMessage
import com.tabiiki.kotlinlab.factory.SignalValue
import com.tabiiki.kotlinlab.factory.StationFactory
import com.tabiiki.kotlinlab.model.Commuter
import com.tabiiki.kotlinlab.util.LineBuilder
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.util.UUID

class StationServiceImplTest {

    private val signalService = mock(SignalService::class.java)
    private val stationFactory = mock(StationFactory::class.java)

    private val stationService =
        StationServiceImpl(timeStep = 10, signalService = signalService, stationFactory = stationFactory)

    private val channel: Channel<SignalMessage> = Channel()

    @BeforeEach
    fun init() {
        `when`(stationFactory.get()).thenReturn(LineBuilder().stations.map { it.id })
        LineBuilder().stations.forEach {
            `when`(stationFactory.get(it.id)).thenReturn(it)
        }

        `when`(signalService.getPlatformSignals()).thenReturn(listOf(Pair("", LineBuilder().stations[0].id)))
        `when`(signalService.getChannel(Pair("", LineBuilder().stations[0].id))).thenReturn(channel)
    }

    @Test
    fun testChannels() = runBlocking {
        val listener: Channel<StationMessage> = Channel()
        val globalCommuterChannel = Channel<Commuter>()

        val job = launch { stationService.start(listener, globalCommuterChannel) }

        val startTime = System.currentTimeMillis()
        launch {
            channel.send(
                SignalMessage(
                    signalValue = SignalValue.GREEN,
                    id = UUID.randomUUID(),
                    key = Pair("", ""),
                    line = "test",
                    commuterChannel = globalCommuterChannel
                )
            )
        }
        var received: Boolean
        do {
            val msg = listener.receive()
            received = msg.type == MessageType.DEPART
        } while (!received && System.currentTimeMillis() < startTime + 100)

        assertThat(received).isEqualTo(true)

        job.cancel()
    }
}
