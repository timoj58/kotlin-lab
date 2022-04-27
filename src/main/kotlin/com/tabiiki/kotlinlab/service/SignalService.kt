package com.tabiiki.kotlinlab.service

import com.tabiiki.kotlinlab.factory.SignalFactory
import com.tabiiki.kotlinlab.factory.SignalType
import com.tabiiki.kotlinlab.factory.SignalValue
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.springframework.stereotype.Service

interface SignalService {
    suspend fun start(key: Pair<String, String>, channelIn: Channel<SignalValue>, channelOut: Channel<SignalValue>)
    fun getPlatformSignals(): List<Pair<String, String>>
    fun getSectionSignals(): List<Pair<String, String>>
}

@Service
class SignalServiceImpl(
    private val signalFactory: SignalFactory
) : SignalService {
    override suspend fun start(
        key: Pair<String, String>,
        channelIn: Channel<SignalValue>,
        channelOut: Channel<SignalValue>
    ): Unit = coroutineScope {
        launch { signalFactory.get(key).start(channelIn, channelOut) }
    }

    override fun getPlatformSignals(): List<Pair<String, String>> =
        signalFactory.get().filter {
            it.type == SignalType.PLATFORM
        }.map { it.section }

    override fun getSectionSignals(): List<Pair<String, String>> =
        signalFactory.get().filter {
            it.type == SignalType.SECTION
        }.map { it.section }
}