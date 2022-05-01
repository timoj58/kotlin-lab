package com.tabiiki.kotlinlab.service

import com.tabiiki.kotlinlab.model.Line
import com.tabiiki.kotlinlab.model.Status
import com.tabiiki.kotlinlab.model.Transport
import com.tabiiki.kotlinlab.repo.StationRepo
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.springframework.stereotype.Service
import java.util.concurrent.atomic.AtomicInteger

interface LineConductor {
    fun getFirstTransportersToDispatch(lines: List<Line>): List<Transport>
    fun getNextTransportersToDispatch(lines: List<Line>): List<Transport>
    suspend fun release(transport: Transport)
    suspend fun start(line: String, lines: List<Line>)
    fun clear(transport: Transport): Boolean
}

@Service
class LineConductorImpl(
    private val lineSectionService: LineSectionService
) : LineConductor {

    override fun getFirstTransportersToDispatch(lines: List<Line>): List<Transport> =
        lines.map { it.transporters }.flatten().groupBy { it.section() }.values.flatten()
            .distinctBy { it.section() }

    override fun getNextTransportersToDispatch(lines: List<Line>): List<Transport> =
        lines.map { it.transporters }.flatten().filter { it.status == Status.DEPOT }
            .groupBy { it.section() }.values.flatten().distinctBy { it.section() }

    override suspend fun release(
        transport: Transport
    ): Unit = coroutineScope {
        delay(transport.timeStep)
        launch { lineSectionService.release(transport) }
    }

    override suspend fun start(line: String, lines: List<Line>): Unit = coroutineScope {
        launch { lineSectionService.start(line, lines) }
    }

    override fun clear(transport: Transport): Boolean = lineSectionService.clear(transport.platformKey())

}
