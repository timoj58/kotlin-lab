package com.tabiiki.kotlinlab.factory

import com.tabiiki.kotlinlab.model.Commuter
import com.tabiiki.kotlinlab.model.Line
import com.tabiiki.kotlinlab.repo.LineDirection
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.springframework.stereotype.Component
import java.util.UUID

enum class SignalType {
    PLATFORM, SECTION, TEST
}

enum class SignalValue {
    RED, GREEN
}

data class SignalMessage(
    val signalValue: SignalValue,
    val id: UUID? = null,
    val key: Pair<String, String>? = null,
    val line: String? = null,
    val commuterChannel: Channel<Commuter>? = null,
    var timesStamp: Long = System.currentTimeMillis(),
    val init: Boolean = false,
)

data class Signal(
    var section: Pair<String, String>,
    val type: SignalType = SignalType.SECTION,
    var status: SignalMessage = SignalMessage(
        signalValue = SignalValue.GREEN,
        key = section,
        init = true
    ),
    val timeStep: Long = 10,
) {
    suspend fun start(channelIn: Channel<SignalMessage>, channelOut: Channel<SignalMessage>) = coroutineScope {
        launch { receive(channelIn) }
        launch { send(channelOut) }
    }

    private suspend fun receive(channel: Channel<SignalMessage>) {
        do {
            val msg = channel.receive()
            if (msg.signalValue != status.signalValue) {
                status = SignalMessage(
                    signalValue = msg.signalValue,
                    id = msg.id,
                    key = msg.key,
                    line = msg.line,
                    commuterChannel = msg.commuterChannel,
                )
            }
        } while (true)
    }

    private suspend fun send(channel: Channel<SignalMessage>) {
        do {
            channel.send(status.also { it.timesStamp = System.currentTimeMillis() })
            delay(timeStep)
        } while (true)
    }

}

@Component
class SignalFactory(
    private val lineFactory: LineFactory
) {
    private var signals = mutableMapOf<Pair<String, String>, Signal>()

    init {
        val lines = lineFactory.get().map { lineFactory.get(it) }
        lines.forEach { line ->
            getLineSections(line).forEach { section ->
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
            pairs.addAll(line.stations.map { Pair("$id:${LineDirection.POSITIVE}", "$id:$it") })
            pairs.addAll(line.stations.map { Pair("$id:${LineDirection.NEGATIVE}", "$id:$it") })
            pairs.addAll(line.stations.filter { lineFactory.isSwitchStation(id, it) }
                .map { Pair("$id:${LineDirection.TERMINAL}", "$id:$it") })
        }
        return pairs.toSet()
    }

    private fun getLineSections(line: Line): Set<Pair<String, String>> {
        val pairs = mutableSetOf<Pair<String, String>>()
        for (station in 0..line.stations.size - 2 step 1) {
            val positive = line.stations[station]
            val positiveNext = line.stations[station + 1]
            val negative = line.stations.reversed()[station]
            val negativeNext = line.stations.reversed()[station + 1]

            val positiveSection = Pair("${line.name}:$positive", positiveNext)
            val negativeSection = Pair("${line.name}:$negative", negativeNext)

            pairs.add(positiveSection)
            pairs.add(negativeSection)
        }
        val first = line.stations.first()
        if (lineFactory.isSwitchStation(line.name, first)) {
            pairs.add(Pair("${line.name}:$first" + "|", first))
            pairs.add(Pair("${line.name}:$first", "$first|"))
        }

        val last = line.stations.last()
        if (lineFactory.isSwitchStation(line.name, last)) {
            pairs.add(Pair("${line.name}:$last" + "|", last))
            pairs.add(Pair("${line.name}:$last", "$last|"))
        }

        return pairs.toSet()
    }

}