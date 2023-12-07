package com.tabiiki.kotlinlab.service

import com.tabiiki.kotlinlab.factory.SignalFactory
import com.tabiiki.kotlinlab.factory.SignalMessage
import com.tabiiki.kotlinlab.factory.SignalType
import com.tabiiki.kotlinlab.repo.LineRepo
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.springframework.stereotype.Service

data class ChannelInfo(
    var channel: Channel<SignalMessage>,
    var latest: SignalMessage? = null
)

private class Channels {
    private val channelsIn: MutableMap<Pair<String, String>, ChannelInfo> =
        HashMap()
    private val channelsOut: MutableMap<Pair<String, String>, ChannelInfo> =
        HashMap()

    fun initIn(key: Pair<String, String>): Channel<SignalMessage> {
        channelsIn[key] = ChannelInfo(channel = Channel())
        return channelsIn[key]!!.channel
    }

    fun initOut(key: Pair<String, String>): Channel<SignalMessage> {
        channelsOut[key] = ChannelInfo(channel = Channel())
        return channelsOut[key]!!.channel
    }

    suspend fun send(key: Pair<String, String>, signalMessage: SignalMessage) {
        channelsIn[key]?.channel?.send(signalMessage)
        channelsIn[key]?.latest = signalMessage
    }

    suspend fun receive(key: Pair<String, String>): SignalMessage? = channelsOut[key]?.channel?.receive()
    fun getChannel(key: Pair<String, String>): Channel<SignalMessage>? = channelsOut[key]?.channel

    fun getLastMessage(key: Pair<String, String>): SignalMessage? = channelsIn[key]?.latest
}

@Service
class SignalService(
    private val signalFactory: SignalFactory
) {
    private val channels = Channels()

    suspend fun init(
        key: Pair<String, String>
    ): Unit = coroutineScope {
        val channelIn = channels.initIn(key)
        val channelOut = channels.initOut(key)

        launch { signalFactory.get(key).start(channelIn, channelOut) }
    }

    fun getPlatformSignals(): List<Pair<String, String>> =
        signalFactory.get(SignalType.PLATFORM).map { it.section }

    fun getSectionSignals(): List<Pair<String, String>> =
        signalFactory.get(SignalType.SECTION).map { it.section }

    fun getChannel(key: Pair<String, String>): Channel<SignalMessage>? = channels.getChannel(key)

    fun getLastMessage(key: Pair<String, String>): SignalMessage? = channels.getLastMessage(key)

    suspend fun receive(key: Pair<String, String>): SignalMessage? = channels.receive(key)
    suspend fun send(
        key: Pair<String, String>,
        signalMessage: SignalMessage
    ) {
        signalFactory.get(key).connected.forEach { signal ->
            channels.send(key = signal, signalMessage = signalMessage)
        }
        channels.send(key = key, signalMessage = signalMessage)
    }

    fun initConnected(line: String, lineRepo: LineRepo) = signalFactory.updateConnected(line, lineRepo)
}
