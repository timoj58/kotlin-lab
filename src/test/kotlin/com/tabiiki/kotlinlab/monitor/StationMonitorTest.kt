package com.tabiiki.kotlinlab.monitor

import com.tabiiki.kotlinlab.factory.SignalMessage
import com.tabiiki.kotlinlab.factory.SignalValue
import com.tabiiki.kotlinlab.model.Commuter
import com.tabiiki.kotlinlab.service.RouteEnquiry
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.mockito.Mockito.atLeastOnce
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import java.util.UUID

class StationMonitorTest {

    private val stationMonitor = StationMonitor()

    @Test
    fun `commuters test`() = runBlocking {
        val transporterId = UUID.randomUUID()

        val platformChannel = Channel<SignalMessage>()
        val stationChannel = Channel<SignalMessage>()

        val commuterChannel = Channel<Commuter>()
        val routeEnquiryChannel = Channel<RouteEnquiry>()
        val carriageChannel = mock(Channel::class.java) as Channel<Commuter>?
        val job = launch { stationMonitor.monitorCommuters(commuterChannel) }
        val job2 = launch { stationMonitor.monitorPlatform(platformChannel, stationChannel) }

        //add a commuter.
        val commuter = Commuter(
            commute = Pair("A", "B"),
            stationChannel = Channel(),
            timeStep = 10,
            routeChannel = routeEnquiryChannel
        ) {}
        commuterChannel.send(commuter)
        //send a RED
        launch {
            platformChannel.send(
                SignalMessage(
                    id = transporterId,
                    signalValue = SignalValue.RED,
                    commuterChannel = carriageChannel,
                    key = Pair("A", "B")
                )
            )
        }

        delay(100)

        verify(carriageChannel!!, atLeastOnce()).send(commuter)

        // send a green to cancel embark.
        launch {
            platformChannel.send(
                SignalMessage(
                    id = transporterId,
                    signalValue = SignalValue.GREEN,
                    commuterChannel = carriageChannel,
                    key = Pair("A", "B")
                )
            )
        }

        delay(100)

        job.cancel()
        job2.cancel()
    }
}