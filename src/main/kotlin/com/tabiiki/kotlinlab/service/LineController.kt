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

data class LineChannelMessage(
    val line: String,
    val section: Pair<String, String>,
    val transporters: List<UUID>
)

interface LineController {
    fun init(commuterChannel: Channel<Commuter>)
    suspend fun init(line: List<Line>)
    suspend fun start(line: List<Line>)

    fun dump()
}

@Service
class LineControllerImpl(
    @Value("\${network.time-step}") private val timeStep: Long,
    private val conductor: LineConductor
) : LineController {

    override fun init(commuterChannel: Channel<Commuter>) = conductor.init(commuterChannel)

    override suspend fun init(line: List<Line>): Unit = coroutineScope {
        launch { conductor.init(line.map { it.name }.distinct().first(), line) }
    }

    override suspend fun start(line: List<Line>): Unit = coroutineScope {
        val transportersToDispatch = conductor.getTransportersToDispatch(line)
        val linesToDispatch = mutableListOf<MutableList<Transport>>()

        line.map { it.id }.sortedBy { getLineIdAsInt(it) }.forEach { lineId ->
            linesToDispatch.add(transportersToDispatch.filter { it.line.id == lineId }.toMutableList())
        }

        launch { dispatchByLineId(line.first().name, linesToDispatch) }
    }

    override fun dump() = conductor.dump()

    private suspend fun dispatchByLineId(line: String, linesToDispatch: MutableList<MutableList<Transport>>) {
        val transportersToDispatch = linesToDispatch.removeFirst()
        val released = mutableListOf<UUID>()

        transportersToDispatch.distinctBy { it.section() }.forEach {
            released.add(it.id)
            release(it)
        }

        transportersToDispatch.removeAll { released.contains(it.id) }

        do {
            released.clear()
            delay(timeStep * startDelayScalar)

            transportersToDispatch.distinctBy { it.section() }.forEach {
                if (conductor.isClear(it)) {
                    released.add(it.id)
                    release(it)
                }
            }
            transportersToDispatch.removeAll { released.contains(it.id) }
        } while (transportersToDispatch.isNotEmpty())

        if (linesToDispatch.isNotEmpty()) dispatchByLineId(line, linesToDispatch)
    }

    private suspend fun release(transport: Transport) = coroutineScope {
        launch { conductor.release(transport = transport) }
    }

    companion object {
        private const val startDelayScalar = 200
        fun getLineIdAsInt(id: String): Int = id.substringAfter("-").toInt()
    }
}
