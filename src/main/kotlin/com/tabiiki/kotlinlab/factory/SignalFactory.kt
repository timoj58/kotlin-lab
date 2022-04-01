package com.tabiiki.kotlinlab.factory

import com.tabiiki.kotlinlab.model.Line
import kotlinx.coroutines.channels.Channel
import org.springframework.stereotype.Repository

enum class SignalType {
    PLATFORM, SECTION
}

enum class SignalValue {
    RED, GREEN, AMBER
}

data class Signal(
    var section: Pair<String, String>,
    private var status: SignalValue = SignalValue.GREEN,
    val type: SignalType = SignalType.SECTION
) {
    fun getStatus() = status
    suspend fun receive(channel: Channel<SignalValue>){
        do{
            status = channel.receive()
        }while (true)
    }

    suspend fun send(channel: Channel<Signal>) {
        do {
            channel.send(this)
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
                val signal = Signal(section = section, type = SignalType.SECTION)
                signals[section] = signal
            }
        }
        getPlatforms(lines).forEach { platform ->
            val signal = Signal(section = platform, type = SignalType.PLATFORM)
            signals[platform] = signal
        }
    }

    fun get(section: Pair<String, String>): Signal = signals[section]!!
    fun get(): List<Signal> = signals.values.toList()

    private fun getPlatforms(lines: List<Line>): Set<Pair<String, String>> {
        var pairs = mutableSetOf<Pair<String, String>>()
        lines.forEach { line ->
            pairs.addAll(line.stations.map { Pair(line.id, it) })
        }
        return pairs.toSet()
    }

    private fun getLineSections(stations: List<String>): Set<Pair<String, String>> {
        var pairs = mutableSetOf<Pair<String, String>>()
        for (station in 0..stations.size - 2 step 1) {
            pairs.add(Pair(stations[station], stations[station + 1]))
            pairs.add(Pair(stations.reversed()[station], stations.reversed()[station + 1]))
        }
        return pairs.toSet()
    }
}