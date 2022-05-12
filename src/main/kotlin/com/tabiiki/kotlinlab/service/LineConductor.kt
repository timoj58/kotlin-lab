package com.tabiiki.kotlinlab.service

import com.tabiiki.kotlinlab.model.Line
import com.tabiiki.kotlinlab.model.Status
import com.tabiiki.kotlinlab.model.Transport
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.springframework.stereotype.Service

interface LineConductor {
    fun getFirstTransportersToDispatch(lines: List<Line>): List<Transport>
    fun getNextTransportersToDispatch(lines: List<Line>): List<Transport>
    suspend fun release(transport: Transport)
    suspend fun start(line: String, lines: List<Line>)
    fun isClear(transport: Transport): Boolean
    fun diagnostics()
}

@Service
class LineConductorImpl(
    private val lineService: LineService
) : LineConductor {

    override fun getFirstTransportersToDispatch(lines: List<Line>): List<Transport> =
        lines.map { it.transporters }.flatten().groupBy { it.section() }.values.flatten()
            .distinctBy { it.section().first }

    override fun getNextTransportersToDispatch(lines: List<Line>): List<Transport> =
        lines.map { it.transporters }.flatten().filter { it.status == Status.DEPOT }
            .groupBy { it.section() }.values.flatten().distinctBy { it.section().first }

    override suspend fun release(
        transport: Transport
    ): Unit = coroutineScope {
        delay(transport.timeStep)
        launch { lineService.release(transport) }
    }

    override suspend fun start(line: String, lines: List<Line>): Unit = coroutineScope {
        launch { lineService.start(line, lines) }
    }

    override fun isClear(transport: Transport): Boolean = lineService.isClear(transport)
    override fun diagnostics() {
        lineService.dump()
    }

}
