package com.tabiiki.kotlinlab.service

import com.tabiiki.kotlinlab.factory.Signal
import com.tabiiki.kotlinlab.factory.SignalFactory
import com.tabiiki.kotlinlab.factory.SignalMessage
import com.tabiiki.kotlinlab.factory.SignalType
import com.tabiiki.kotlinlab.factory.SignalValue
import com.tabiiki.kotlinlab.repo.LineRepo
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap


private class Channels {
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

interface SignalService {
    suspend fun init(key: Pair<String, String>)
    fun getSignal(key: Pair<String, String>): Signal
    fun getPlatformSignals(): List<Pair<String, String>>
    fun getSectionSignals(): List<Pair<String, String>>
    fun getChannel(key: Pair<String, String>): Channel<SignalMessage>?
    suspend fun receive(key: Pair<String, String>): SignalMessage?
    suspend fun send(
        key: Pair<String, String>,
        signalMessage: SignalMessage,
        priorities: List<Pair<Pair<String, String>, Pair<Boolean, Int>>> = listOf()
    )

    fun initConnected(line: String, lineRepo: LineRepo)
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

    override fun getSignal(key: Pair<String, String>): Signal = signalFactory.get(key)

    override fun getPlatformSignals(): List<Pair<String, String>> =
        signalFactory.get(SignalType.PLATFORM).map { it.section }

    override fun getSectionSignals(): List<Pair<String, String>> =
        signalFactory.get(SignalType.SECTION).map { it.section }

    override fun getChannel(key: Pair<String, String>): Channel<SignalMessage>? = channels.getChannel(key)

    override suspend fun receive(key: Pair<String, String>): SignalMessage? = channels.receive(key)
    override suspend fun send(
        key: Pair<String, String>,
        signalMessage: SignalMessage,
        priorities: List<Pair<Pair<String, String>, Pair<Boolean, Int>>>
    ) {
        testForTerminalSectionAndSend(key = key, signalMessage = signalMessage)

        val priority = priorities.filter { it.second.second != 0 }.sortedByDescending { it.second.second }
            .firstOrNull { !it.second.first }

        priority?.let { channels.send(key = it.first, signalMessage = signalMessage) }
        signalFactory.get(key).connected.forEach { signal ->
            if (priority == null || priorities.first { it.first == signal }.second.first)
                channels.send(key = signal, signalMessage = signalMessage)
        }
    }

    override fun initConnected(line: String, lineRepo: LineRepo) = signalFactory.updateConnected(line, lineRepo)

    private suspend fun testForTerminalSectionAndSend(key: Pair<String, String>, signalMessage: SignalMessage) {
        //purpose of this without amber etc, is to ensure a transporter in the main section can disable the terminal section into the line
        //tidy this up eventually if it works.
        signalFactory.get(key).connected.firstOrNull { it.second.contains("|") }?.let {
           if(signalMessage.signalValue == SignalValue.GREEN)
               channels.send(
                   key = Pair("${it.first}|", it.second.replace("|", "")),
                   signalMessage = signalMessage.also { msg -> msg.signalValue = SignalValue.RED }
               )
        }
        channels.send(key = key, signalMessage = signalMessage)
    }

}