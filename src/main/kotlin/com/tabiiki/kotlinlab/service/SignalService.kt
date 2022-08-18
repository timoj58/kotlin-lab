package com.tabiiki.kotlinlab.service

import com.tabiiki.kotlinlab.factory.SignalFactory
import com.tabiiki.kotlinlab.factory.SignalMessage
import com.tabiiki.kotlinlab.factory.SignalType
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap

interface SignalService {
    suspend fun init(key: Pair<String, String>)
    fun getPlatformSignals(): List<Pair<String, String>>
    fun getSectionSignals(): List<Pair<String, String>>
    fun getChannel(key: Pair<String, String>): Channel<SignalMessage>?
    suspend fun receive(key: Pair<String, String>): SignalMessage?
    suspend fun send(key: Pair<String, String>, signalMessage: SignalMessage)
}

@Service
class SignalServiceImpl(
    private val signalFactory: SignalFactory
) : SignalService {
    private val channels = Channels()

    override suspend fun init(
        key: Pair<String, String>
    ): Unit = coroutineScope {
        val channelIn = channels.initIn(key)
        val channelOut = channels.initOut(key)

        launch { signalFactory.get(key).start(channelIn, channelOut) }
    }

    override fun getPlatformSignals(): List<Pair<String, String>> =
        signalFactory.get().filter { it.type == SignalType.PLATFORM }.map { it.section }

    override fun getSectionSignals(): List<Pair<String, String>> =
        signalFactory.get().filter { it.type == SignalType.SECTION }.map { it.section }

    override fun getChannel(key: Pair<String, String>): Channel<SignalMessage>? = channels.getChannel(key)

    override suspend fun receive(key: Pair<String, String>): SignalMessage? = channels.receive(key)
    override suspend fun send(key: Pair<String, String>, signalMessage: SignalMessage) =
        channels.send(key, signalMessage)


    companion object {
        class Channels {
            private val channelsIn: ConcurrentHashMap<Pair<String, String>, Channel<SignalMessage>> =
                ConcurrentHashMap()
            private val channelsOut: ConcurrentHashMap<Pair<String, String>, Channel<SignalMessage>> =
                ConcurrentHashMap()

            fun initIn(key: Pair<String, String>): Channel<SignalMessage> {
                channelsIn[key] = Channel()
                return channelsIn[key]!!
            }

            fun initOut(key: Pair<String, String>): Channel<SignalMessage> {
                channelsOut[key] = Channel()
                return channelsOut[key]!!
            }

            suspend fun send(key: Pair<String, String>, signalMessage: SignalMessage) =
                channelsIn[key]?.send(signalMessage) ?: throw RuntimeException("bad channel $key")

            suspend fun receive(key: Pair<String, String>): SignalMessage? = channelsOut[key]?.receive()
            fun getChannel(key: Pair<String, String>): Channel<SignalMessage>? = channelsOut[key]
        }
    }
}