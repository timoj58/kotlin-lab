package com.tabiiki.kotlinlab.service

import com.tabiiki.kotlinlab.model.Commuter
import com.tabiiki.kotlinlab.model.Line
import com.tabiiki.kotlinlab.model.Transport
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.springframework.stereotype.Service

@Service
class LineController(
    private val conductor: LineConductor
) {

    suspend fun init(commuterChannel: Channel<Commuter>) = coroutineScope {
        launch { conductor.init(commuterChannel = commuterChannel) }
    }

    suspend fun start(line: List<Line>): Unit = coroutineScope {
        val transportersToDispatch = conductor.getTransportersToDispatch(line)
        val linesToDispatch = mutableListOf<Transport>()

        line.map { it.id }.sortedBy { getLineIdAsInt(it) }.forEach { lineId ->
            linesToDispatch.addAll(transportersToDispatch.filter { it.line.id == lineId }.toMutableList())
        }

        launch { dispatch(toDispatch = linesToDispatch) }
    }

    private suspend fun dispatch(toDispatch: MutableList<Transport>) = coroutineScope {
        val toRelease = toDispatch.distinctBy { it.section() }
        toRelease.forEach {
            release(it)
            toDispatch.remove(it)
        }
        if (toDispatch.isNotEmpty()) {
            delay(toDispatch.first().timeStep * 100)
            launch { conductor.buffer(toDispatch) }
        }
    }

    private suspend fun release(transport: Transport) = coroutineScope {
        launch { conductor.release(transport = transport) }
    }

    companion object {
        fun getLineIdAsInt(id: String): Int = id.substringAfter("-").toInt()
    }
}
