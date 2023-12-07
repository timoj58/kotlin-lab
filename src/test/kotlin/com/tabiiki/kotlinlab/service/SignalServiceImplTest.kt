package com.tabiiki.kotlinlab.service

import com.tabiiki.kotlinlab.factory.Signal
import com.tabiiki.kotlinlab.factory.SignalFactory
import com.tabiiki.kotlinlab.factory.SignalMessage
import com.tabiiki.kotlinlab.factory.SignalType
import com.tabiiki.kotlinlab.factory.SignalValue
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

internal class SignalServiceImplTest {

    private val signalFactory = mock(SignalFactory::class.java)
    private val signalService = SignalService(signalFactory)

    @BeforeEach
    fun init() {
        `when`(signalFactory.get(Pair("A", "B"))).thenReturn(
            Signal(section = Pair("A", "B"), type = SignalType.TEST)
        )
    }

    @Test
    fun `test signal`() = runBlocking {
        val job = async { signalService.init(Pair("A", "B")) }

        delay(100)
        val channelOut = signalService.getChannel(Pair("A", "B"))!!

        val job2 = async { testChannel(channelOut, job) }

        delay(100)
        signalService.send(
            Pair("A", "B"),
            SignalMessage(
                SignalValue.RED,
                line = null
            )
        )
        delay(200)
        job2.cancel()
    }

    suspend fun testChannel(channel: Channel<SignalMessage>, job: Job) {
        var signal: SignalValue?
        do {
            signal = channel.receive().signalValue
        } while (signal != SignalValue.RED)

        job.cancel()
    }
}
