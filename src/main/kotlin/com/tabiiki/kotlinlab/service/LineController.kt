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
        println("starting ${line.first().name}")
        val transportersToDispatch = conductor.getTransportersToDispatch(line)
        val linesToDispatch = mutableListOf<Transport>()

        line.map { it.id }.sortedBy { getLineIdAsInt(it) }.forEach { lineId ->
            linesToDispatch.addAll(transportersToDispatch.filter { it.line.id == lineId }.toMutableList())
        }

        launch { dispatch(toDispatch = linesToDispatch) }
        println("finishing ${line.first().name}")
    }

    private suspend fun dispatch(toDispatch: MutableList<Transport>) = coroutineScope {
        val line = toDispatch.first().line.name
        val toRelease = toDispatch.distinctBy { it.section() }
        toRelease.forEach {
            release(it)
            toDispatch.remove(it)
        }
        if (toDispatch.isNotEmpty()) {
            delay(toDispatch.first().timeStep * 100)
            launch { conductor.buffer(toDispatch) }
        }

        println("completed $line")
    }

    private suspend fun release(transport: Transport) = coroutineScope {
        launch { conductor.release(transport = transport) }
    }

    companion object {
        fun getLineIdAsInt(id: String): Int = id.substringAfter("-").toInt()
    }
}
