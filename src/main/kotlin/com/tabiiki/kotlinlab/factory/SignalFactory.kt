package com.tabiiki.kotlinlab.factory

import com.tabiiki.kotlinlab.model.Line
import com.tabiiki.kotlinlab.service.LineDirection
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Repository

enum class SignalType {
    PLATFORM, SECTION
}

enum class SignalValue {
    RED, GREEN, AMBER_10, AMBER_20, AMBER_30
}

data class Signal(
    var section: Pair<String, String>,
    var status: SignalValue = SignalValue.GREEN,
    val type: SignalType = SignalType.SECTION,
    val timeStep: Long = 100
) {
    suspend fun start(channelIn: Channel<SignalValue>, channelOut: Channel<SignalValue>) = coroutineScope {
        launch { receive(channelIn) }
        launch { send(channelOut) }
    }

    private suspend fun receive(channel: Channel<SignalValue>) {
        do {
            val msg = channel.receive()
            if (msg != status) {
                status = msg
            }
        } while (true)
    }

    private suspend fun send(channel: Channel<SignalValue>) {
        do {
            channel.send(this.status)
            delay(timeStep) //take this out?
        } while (true)
    }

}

@Repository
class SignalFactory(
    lineFactory: LineFactory
) {
    private var signals = mutableMapOf<Pair<String, String>, Signal>()

    init {
        val lines = lineFactory.get().map { lineFactory.get(it) }
        lines.forEach { line ->
            getLineSections(line.stations).forEach { section ->
                val signal = Signal(section = section, type = SignalType.SECTION, timeStep = lineFactory.timeStep)
                signals[section] = signal
            }
        }
        getPlatforms(lines).forEach { platform ->
            val signal = Signal(section = platform, type = SignalType.PLATFORM, timeStep = lineFactory.timeStep)
            signals[platform] = signal
        }
    }

    fun get(section: Pair<String, String>): Signal = signals[section]!!
    fun get(): List<Signal> = signals.values.toList()

    private fun getPlatforms(lines: List<Line>): Set<Pair<String, String>> {
        val pairs = mutableSetOf<Pair<String, String>>()
        lines.forEach { line ->
            val id = line.name
            pairs.addAll(line.stations.map { Pair("$id ${LineDirection.POSITIVE}", it) })
            pairs.addAll(line.stations.map { Pair("$id ${LineDirection.NEGATIVE}", it) })
        }
        return pairs.toSet()
    }

    private fun getLineSections(stations: List<String>): Set<Pair<String, String>> {
        val pairs = mutableSetOf<Pair<String, String>>()
        for (station in 0..stations.size - 2 step 1) {
            pairs.add(Pair(stations[station], stations[station + 1]))
            pairs.add(Pair(stations.reversed()[station], stations.reversed()[station + 1]))
        }
        return pairs.toSet()
    }
}