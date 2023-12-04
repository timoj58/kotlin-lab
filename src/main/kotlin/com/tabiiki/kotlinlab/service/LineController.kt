package com.tabiiki.kotlinlab.service

import com.tabiiki.kotlinlab.model.Commuter
import com.tabiiki.kotlinlab.model.Line
import com.tabiiki.kotlinlab.model.Transport
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.util.UUID
@Service
class LineController(
    @Value("\${network.time-step}") private val timeStep: Long,
    private val conductor: LineConductor
) {

    fun init(commuterChannel: Channel<Commuter>) = conductor.init(commuterChannel)

    suspend fun init(line: List<Line>): Unit = coroutineScope {
        launch { conductor.init(line.map { it.name }.distinct().first(), line) }
    }

    suspend fun start(line: List<Line>): Unit = coroutineScope {
        val transportersToDispatch = conductor.getTransportersToDispatch(line)
        val linesToDispatch = mutableListOf<Transport>()

        line.map { it.id }.sortedBy { getLineIdAsInt(it) }.forEach { lineId ->
            linesToDispatch.addAll(transportersToDispatch.filter { it.line.id == lineId }.toMutableList())
        }

        launch { dispatch(linesToDispatch) }
    }

    private suspend fun dispatch(toDispatch: MutableList<Transport>) {
        val released = mutableListOf<UUID>()

        toDispatch.distinctBy { it.section() }.forEach {
            released.add(it.id)
            release(it)
        }

        toDispatch.removeAll { released.contains(it.id) }

        do {
            released.clear()
            delay(timeStep * startDelayScalar)

            toDispatch.distinctBy { it.section() }.forEach {
                if (conductor.isClear(it)) {
                    released.add(it.id)
                    release(it)
                }
            }
            toDispatch.removeAll { released.contains(it.id) }
        } while (toDispatch.isNotEmpty())
    }

    private suspend fun release(transport: Transport) = coroutineScope {
        launch { conductor.release(transport = transport) }
    }

    companion object {
        private const val startDelayScalar = 100
        fun getLineIdAsInt(id: String): Int = id.substringAfter("-").toInt()
    }
}
