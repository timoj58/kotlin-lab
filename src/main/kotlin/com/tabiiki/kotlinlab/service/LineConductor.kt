package com.tabiiki.kotlinlab.service

import com.tabiiki.kotlinlab.model.Commuter
import com.tabiiki.kotlinlab.model.Line
import com.tabiiki.kotlinlab.model.Transport
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.springframework.stereotype.Service

interface LineConductor {
    fun getTransportersToDispatch(lines: List<Line>): MutableList<Transport>
    suspend fun release(transport: Transport)
    suspend fun init(line: String, lines: List<Line>)
    fun init(commuterChannel: Channel<Commuter>)
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
        platformService.signalAndDispatch(transport = transport)
    }

    override suspend fun init(line: String, lines: List<Line>): Unit = coroutineScope {
        launch { platformService.init(line, lines) }
    }

    override fun init(commuterChannel: Channel<Commuter>) {
        platformService.init(commuterChannel)
    }

    override fun isClear(transport: Transport): Boolean =
        platformService.isClear(transport) && platformService.canLaunch(transport)

}
