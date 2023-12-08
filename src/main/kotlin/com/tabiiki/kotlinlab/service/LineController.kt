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
        launch { dispatch(toDispatch = conductor.getTransportersToDispatch(line).toMutableList()) }
    }

    private suspend fun dispatch(toDispatch: MutableList<Transport>) = coroutineScope {
        val toRelease = toDispatch.distinctBy { it.section() }
        toRelease.forEach {
            toDispatch.remove(it)
            launch { conductor.release(transport = it) }
        }
        if (toDispatch.isNotEmpty()) {
            launch { conductor.buffer(toDispatch) }
        }
    }
}
