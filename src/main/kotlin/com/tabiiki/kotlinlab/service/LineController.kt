package com.tabiiki.kotlinlab.service

import com.tabiiki.kotlinlab.model.Commuter
import com.tabiiki.kotlinlab.model.Line
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.util.UUID
import javax.naming.ConfigurationException

data class LineChannelMessage(
    val line: String,
    val section: Pair<String, String>,
    val transporters: List<UUID>
)

interface LineController {
    fun init(commuterChannel: Channel<Commuter>)
    suspend fun init(line: List<Line>)
    suspend fun start(line: List<Line>)
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

    override suspend fun start(line: List<Line>) = coroutineScope {
        val released = mutableListOf<UUID>()
        val transportersToDispatch = conductor.getTransportersToDispatch(line)

        transportersToDispatch.distinctBy { it.section() }.forEach {
            released.add(it.id)
            launch { conductor.release(transport = it) }
        }

        transportersToDispatch.removeAll { released.contains(it.id) }

        do {
            released.clear()
            delay(timeStep * startDelayScalar)

            transportersToDispatch.distinctBy { it.section() }.forEach {
                if (conductor.isClear(it)) {
                    released.add(it.id)
                    launch { conductor.release(transport = it) }
                }
            }
            transportersToDispatch.removeAll { released.contains(it.id) }

        } while (transportersToDispatch.isNotEmpty())

    }

    companion object {
        private const val startDelayScalar = 300 //ie 5 minutes, if timestep is 1 second
    }
}