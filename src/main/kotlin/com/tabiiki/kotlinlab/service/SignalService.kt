package com.tabiiki.kotlinlab.service

import com.tabiiki.kotlinlab.factory.LineFactory
import com.tabiiki.kotlinlab.factory.Signal
import com.tabiiki.kotlinlab.factory.SignalFactory
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import org.springframework.stereotype.Service

interface SignalService {
    suspend fun start()
    suspend fun monitor(channels: List<Channel<Signal>>)
}

@Service
class SignalServiceImpl(
    private val signalFactory: SignalFactory,
    private val lineFactory: LineFactory
): SignalService {
    private val signals = Channel<Signal>()
    override suspend fun start() {
         signalFactory.create(lineFactory.get().map { lineFactory.get(it)}, signals)
    }

    override suspend fun monitor(channels: List<Channel<Signal>>) = coroutineScope {
        do {
            val msg = signals.receive()
            channels.forEach { async { it.send(msg) } }
        }while (true)
    }

}