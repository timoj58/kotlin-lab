package com.tabiiki.kotlinlab.service

import com.tabiiki.kotlinlab.model.Instruction
import com.tabiiki.kotlinlab.model.Line
import com.tabiiki.kotlinlab.model.TransportMessage
import kotlinx.coroutines.channels.Channel
import org.springframework.stereotype.Service
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

@Service
class RegulatorService {

    val transportReceiver = Channel<TransportMessage>()
    private val transportersByLine = mutableMapOf<String, MutableMap<UUID, TransportMessage?>>()
    private val timers = mutableMapOf<String, AtomicInteger>()

    fun init(lines: List<Line>) {
        lines.forEach { line ->
            timers[line.name] = AtomicInteger(0)
            transportersByLine[line.name] = line.transporters.associate { it.id to null }.toMutableMap()
        }
    }

    suspend fun regulate() {
        do {
            val msg = transportReceiver.receive()
            transportersByLine[msg.lineName]!![msg.id] = msg
            if (transportersByLine[msg.lineName]!!.values.all { listOf(Instruction.SCHEDULED_STOP, Instruction.EMERGENCY_STOP).contains(it?.instruction) }) {
                timers[msg.lineName]!!.getAndIncrement()
            } else {
                timers[msg.lineName]!!.set(0)
            }
            //   if(timers[msg.lineName]!!.get() > 10000) throw RuntimeException("${msg.lineName} ${transportersByLine[msg.lineName]!!.values.map { Pair(it!!.id, it.section) }}")
        } while (true)
    }
}
