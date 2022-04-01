package com.tabiiki.kotlinlab.factory

import com.tabiiki.kotlinlab.model.Line
import kotlinx.coroutines.channels.Channel
import org.springframework.stereotype.Repository


enum class SignalValue {
    RED, GREEN, AMBER
}

data class Signal(
    var section: Pair<String, String>,
    var channel: Channel<Signal>,
    var status: SignalValue = SignalValue.GREEN
) {

    suspend fun notify() {
        do {
            channel.send(this)
        } while (true)
    }
}

@Repository
class SignalFactory {

    private var signals = mutableMapOf<Pair<String, String>, Signal>()

    fun create(lines: List<Line>, channel: Channel<Signal>) {
        lines.forEach { line ->
            getLineSections(line.stations).forEach { section ->
                signals[section] = Signal(section = section, channel = channel)
            }
        }
    }

    fun get(linePosition: Pair<String, String>): Signal = signals[linePosition]!!

    private fun getLineSections(stations: List<String>): Set<Pair<String, String>> {
        var pairs = mutableSetOf<Pair<String, String>>()
        for (station in 0..stations.size - 2 step 1) {
            pairs.add(Pair(stations[station], stations[station + 1]))
            pairs.add(Pair(stations.reversed()[station], stations.reversed()[station + 1]))
        }
        return pairs.toSet()
    }
}