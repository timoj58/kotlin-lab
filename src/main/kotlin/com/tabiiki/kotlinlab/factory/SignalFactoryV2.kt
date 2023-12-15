package com.tabiiki.kotlinlab.factory

import com.tabiiki.kotlinlab.model.Commuter
import com.tabiiki.kotlinlab.model.Line
import com.tabiiki.kotlinlab.service.LineDirection
import com.tabiiki.kotlinlab.service.PlatformSignalType
import kotlinx.coroutines.channels.Channel
import java.util.UUID

enum class SignalType {
    PLATFORM, SECTION, TEST
}

enum class Origin {
    RELEASE, HOLD, DEPART, INIT, SWITCH, DEFAULT,
}

enum class SignalValue {
    RED, GREEN
}

data class SignalMessageV2(
    val signalValue: SignalValue,
    val line: String?,
    val id: UUID? = null,
    val commuterChannel: Channel<Commuter>? = null,
    val key: Pair<String, String>? = null
)

data class SignalV2(
    val receiver: Channel<SignalMessageV2> = Channel(),
    val consumers: MutableList<Channel<SignalMessageV2>> = mutableListOf(),
    val type: SignalType = SignalType.SECTION,
    var latest: SignalMessageV2 = SignalMessageV2(
        signalValue = SignalValue.GREEN,
        line = null
    ),
    val isTerminal: Boolean = false,
    val key: Pair<String, String>
) {
    suspend fun subscribe(
        channel: Channel<SignalMessageV2>
    ) {
        this.consumers.add(channel)
        channel.send(this.latest)
    }
}

class SignalFactoryV2 {
    fun getSignals(
        lines: List<Line>,
        isSwitchStation: (String, String) -> Boolean,
        previousSections: (Pair<String, String>) -> List<Pair<String, String>>
    ): MutableMap<Pair<String, String>, SignalV2> {
        val signals = mutableMapOf<Pair<String, String>, SignalV2>()

        lines.forEach { line ->
            getLineSections(line, isSwitchStation).forEach { section ->
                val signal = SignalV2(
                    type = SignalType.SECTION,
                    key = section
                )
                signals[section] = signal
            }
        }

        getPlatforms(lines, isSwitchStation).forEach { platform ->
            val signal = SignalV2(
                type = SignalType.PLATFORM,
                isTerminal = platform.first.contains(LineDirection.TERMINAL.name),
                key = platform
            )
            signals[platform] = signal
        }

        signals.values.filter { it.type == SignalType.PLATFORM }.forEach { signal ->
            if (signal.isTerminal) {
                signal.consumers.add(
                    signals[
                        Pair(
                            "${signal.key.first.substringBefore(":")}:${Line.getStation(signal.key.second)}",
                            "${Line.getStation(signal.key.second)}|"
                        )
                    ]!!.receiver
                )
            } else {
                previousSections(signal.key).forEach {
                    signal.consumers.add(
                        signals[it]!!.receiver
                    )
                }
            }
        }

        return signals
    }

    private fun getPlatforms(lines: List<Line>, isSwitchStation: (String, String) -> Boolean): Set<Pair<String, String>> {
        val pairs = mutableSetOf<Pair<String, String>>()
        lines.forEach { line ->
            val id = line.name
            pairs.addAll(line.stations.map { Pair("$id:${LineDirection.POSITIVE}:${PlatformSignalType.ENTRY}", "$id:$it") })
            pairs.addAll(line.stations.map { Pair("$id:${LineDirection.POSITIVE}:${PlatformSignalType.EXIT}", "$id:$it") })
            pairs.addAll(line.stations.map { Pair("$id:${LineDirection.NEGATIVE}:${PlatformSignalType.ENTRY}", "$id:$it") })
            pairs.addAll(line.stations.map { Pair("$id:${LineDirection.NEGATIVE}:${PlatformSignalType.EXIT}", "$id:$it") })
            pairs.addAll(
                line.stations.filter { isSwitchStation(id, it) }
                    .map { Pair("$id:${LineDirection.TERMINAL}", "$id:$it") }
            )
        }
        return pairs.toSet()
    }

    private fun getLineSections(line: Line, isSwitchStation: (String, String) -> Boolean): Set<Pair<String, String>> {
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
        if (isSwitchStation(line.name, first)) {
            pairs.add(Pair("${line.name}:$first" + "|", first))
            pairs.add(Pair("${line.name}:$first", "$first|"))
        }

        val last = line.stations.last()
        if (isSwitchStation(line.name, last)) {
            pairs.add(Pair("${line.name}:$last" + "|", last))
            pairs.add(Pair("${line.name}:$last", "$last|"))
        }

        return pairs.toSet()
    }
}
