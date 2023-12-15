package com.tabiiki.kotlinlab.service

import com.tabiiki.kotlinlab.factory.SignalFactoryV2
import com.tabiiki.kotlinlab.factory.SignalMessageV2
import com.tabiiki.kotlinlab.factory.SignalType
import com.tabiiki.kotlinlab.factory.SignalV2
import com.tabiiki.kotlinlab.factory.SignalValue
import com.tabiiki.kotlinlab.model.Line
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Service
class SignalServiceV2(
    @Value("\${network.time-step}") val timeStep: Long
) {
    private var signals: Map<Pair<String, String>, SignalV2>? = null

    fun init(
        lines: List<Line>,
        isSwitchStation: (String, String) -> Boolean,
        previousSections: (Pair<String, String>) -> List<Pair<String, String>>
    ) {
        signals = SignalFactoryV2().getSignals(
            lines = lines,
            isSwitchStation = isSwitchStation,
            previousSections = previousSections
        )
    }

    fun getPlatformSignals(): List<SignalV2> = signals!!.values.filter { it.type == SignalType.PLATFORM }

    suspend fun monitor() = coroutineScope {
        signals!!.values.forEach {
            launch { monitor(it) }
        }
    }

    suspend fun subscribe(
        key: Pair<String, String>,
        channel: Channel<SignalMessageV2>,
        emit: Boolean = false
    ) = coroutineScope {
        signals!![key]!!.subscribe(channel = channel)

        if (emit) {
            launch { emit(key = key, channel = channel) }
        }
    }

    suspend fun send(key: Pair<String, String>, message: SignalMessageV2) {
      //  println("sending $message")
        signals!![key]!!.receiver.send(message)
    }

    private suspend fun emit(
        key: Pair<String, String>,
        channel: Channel<SignalMessageV2>
    ) {
        do {
            if (!channel.isClosedForReceive) {
                try {
                    channel.send(signals!![key]!!.latest)
                } catch (e: Exception) {
                    return
                }
            }
            delay(timeStep)
        } while (!channel.isClosedForReceive)
    }

    private suspend fun monitor(signal: SignalV2) = coroutineScope {
        do {
            signal.latest = signal.receiver.receive()

       /*     if(signal.latest.key == Pair("Gospel Oak - Barking:NEGATIVE:ENTRY", "Gospel Oak - Barking:29")){
                println("received it ${signal.latest.key} sending to ${signal.children.map { it.key }}")
            }

            if(signal.key == Pair("Gospel Oak - Barking:646", "29") && signal.latest.key == Pair("Gospel Oak - Barking:NEGATIVE:ENTRY", "Gospel Oak - Barking:29") ){
                if(signal.consumers.isNotEmpty()){
                    println("trying to send the message ${signal.latest}")
                }else{
                    println("no consumers ${signal.latest}")
                }
            } */

            launch {
                signal.children.forEach {
                //    if(signal.latest.key == Pair("Gospel Oak - Barking:NEGATIVE:ENTRY", "Gospel Oak - Barking:29")){
                 //       println("sent it ${signal.latest.key} sending to ${signal.children.map { it.key }}")
                 //   }
                  launch {  it.receiver.send(signal.latest) }
                }
            }
            signal.consumers.filter { !it.isClosedForSend }.forEach {
                launch {
                    try {
                        it.send(signal.latest)
                    } catch (_: Exception) {
                    }
                }
            }
            signal.consumers.removeIf { it.isClosedForSend }
        } while (true)
    }
}
