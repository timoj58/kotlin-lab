package com.tabiiki.kotlinlab.service

import com.tabiiki.kotlinlab.model.Commuter
import com.tabiiki.kotlinlab.model.Line
import com.tabiiki.kotlinlab.model.Transport
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.springframework.stereotype.Service

interface LineConductor {
    fun getTransportersToDispatch(lines: List<Line>): MutableList<Transport>
    suspend fun release(transport: Transport)
    suspend fun hold(transport: Transport)
    suspend fun init(line: String, lines: List<Line>, channel: Channel<Commuter>)
    fun isClear(transport: Transport): Boolean
}

@Service
class LineConductorImpl(
    private val platformService: PlatformService
) : LineConductor {
    override fun getTransportersToDispatch(lines: List<Line>): MutableList<Transport> =
        lines.map { it.transporters }.flatten().groupBy { it.section() }.values.flatten().toMutableList()

    override suspend fun release(
        transport: Transport
    ): Unit = coroutineScope {
        delay(transport.timeStep)
        launch { platformService.release(transport) }
    }

    override suspend fun hold(transport: Transport): Unit = coroutineScope {
        launch { platformService.hold(transport) }
    }

    override suspend fun init(line: String, lines: List<Line>, channel: Channel<Commuter>): Unit = coroutineScope {
        launch { platformService.init(line, lines, channel) }
    }

    override fun isClear(transport: Transport): Boolean =
        platformService.isClear(transport) && platformService.canLaunch(transport)
}
