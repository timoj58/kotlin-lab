package com.tabiiki.kotlinlab.service

import com.tabiiki.kotlinlab.factory.Signal
import com.tabiiki.kotlinlab.factory.SignalFactory
import com.tabiiki.kotlinlab.factory.SignalValue
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock

internal class SignalServiceImplTest {

    private val signalFactory = mock(SignalFactory::class.java)
    private val signalService = SignalServiceImpl(signalFactory)

    @BeforeEach
    fun init() {
        `when`(signalFactory.get(Pair("A", "B"))).thenReturn(
            Signal(Pair("A", "B"))
        )
    }

    @Test
    fun `test signal`() = runBlocking {
        val channelIn = Channel<SignalValue>()
        val channelOut = Channel<SignalValue>()

        val job = async { signalService.start(Pair("A", "B"), channelIn, channelOut) }
        val job2 = async { testChannel(channelOut, job) }

        delay(100)
        channelIn.send(SignalValue.AMBER_30)
        delay(200)
        job2.cancelAndJoin()
    }

    suspend fun testChannel(channel: Channel<SignalValue>, job: Job) {
        var signal: SignalValue?
        do {
            signal = channel.receive()
            println(signal)
        } while (signal != SignalValue.AMBER_30)

        job.cancelAndJoin()
    }

}