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
import java.util.concurrent.ConcurrentHashMap
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
    @Value("\${network.start-delay}") private val startDelay: Long,
    private val conductor: LineConductor
) : LineController {
    init {
        if (startDelay < 1000) throw ConfigurationException("start delay is to small, minimum 1000 ms")
    }

    override fun init(commuterChannel: Channel<Commuter>)  = conductor.init(commuterChannel)

    override suspend fun init(line: List<Line>): Unit = coroutineScope {
        launch { conductor.init(line.map { it.name }.distinct().first(), line) }
    }

    override suspend fun start(line: List<Line>) = coroutineScope {
        val released = mutableListOf<UUID>()
        val transportersToDispatch = conductor.getTransportersToDispatch(line)

        transportersToDispatch.distinctBy { it.section() }.forEach {
            launch { conductor.release(it) }
            released.add(it.id)
        }

        transportersToDispatch.removeAll { released.contains(it.id) }

        do {
            released.clear()
            delay(startDelay)

            transportersToDispatch.groupBy { it.section() }.forEach { (_, transporters) ->

                val transport = transporters.first()
                if (conductor.isClear(transport)) {
                    launch { conductor.hold(transport) }
                    released.add(transport.id)
                }
            }
            transportersToDispatch.removeAll { released.contains(it.id) }

        } while (transportersToDispatch.isNotEmpty())

    }

}