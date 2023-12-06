package com.tabiiki.kotlinlab.service

import com.tabiiki.kotlinlab.model.Commuter
import com.tabiiki.kotlinlab.model.Line
import com.tabiiki.kotlinlab.model.Transport
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.springframework.stereotype.Service

@Service
class LineConductor(
    private val platformService: PlatformService
) {

    fun getTransportersToDispatch(lines: List<Line>): MutableList<Transport> =
        lines.map { it.transporters }.flatten().groupBy { it.section() }.values.flatten().toMutableList()

    suspend fun release(
        transport: Transport
    ): Unit = coroutineScope {
        platformService.release(transport = transport)
    }

    suspend fun buffer(
        transporters: MutableList<Transport>
    ) {
        platformService.buffer(transporters)
    }

    suspend fun init(line: String, lines: List<Line>): Unit = coroutineScope {
        launch { platformService.initLines(line, lines) }
    }

    fun init(commuterChannel: Channel<Commuter>) {
        platformService.initCommuterChannel(commuterChannel)
    }
}
