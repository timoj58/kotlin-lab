package com.tabiiki.kotlinlab.monitor

import com.tabiiki.kotlinlab.factory.SignalMessage
import com.tabiiki.kotlinlab.factory.SignalValue
import com.tabiiki.kotlinlab.model.Commuter
import com.tabiiki.kotlinlab.model.Station
import com.tabiiki.kotlinlab.service.MessageType
import com.tabiiki.kotlinlab.service.StationMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class StationMonitor(val timestep: Long = 100) {

    private val stationCommuters: ConcurrentHashMap<String, MutableList<Commuter>> = ConcurrentHashMap()
    private val carriageChannelJobs: ConcurrentHashMap<UUID, Job> = ConcurrentHashMap()

    suspend fun monitorPlatform(
        platformChannel: Channel<SignalMessage>,
        stationChannel: Channel<SignalMessage>,
    ) = coroutineScope {
        var previousSignal: SignalValue? = null
        do {
            val msg = platformChannel.receive()
            msg.commuterChannel?.let {
                when (msg.signalValue) {
                    SignalValue.RED -> carriageChannelJobs[msg.id!!] = launch { embark(msg.key!!, msg.commuterChannel) }
                    SignalValue.GREEN -> carriageChannelJobs[msg.id!!]?.cancel()
                }
            }
            if (previousSignal == null || previousSignal != msg.signalValue) {
                launch { stationChannel.send(msg) }
                previousSignal = msg.signalValue
            }
        } while (true)
    }

    suspend fun monitorStation(
        station: Station,
        stationChannel: Channel<SignalMessage>,
        globalListener: Channel<StationMessage>,
    ) = coroutineScope {
        do {
            val msg = stationChannel.receive()
            msg.id?.let {
                val messageType = if (msg.signalValue == SignalValue.GREEN) MessageType.DEPART else MessageType.ARRIVE
                launch {
                    globalListener.send(
                        StationMessage(
                            stationId = station.id,
                            transportId = msg.id,
                            line = msg.line!!,
                            type = messageType
                        )
                    )
                }
            }
        } while (true)
    }

    suspend fun monitorCommuters(channel: Channel<Commuter>) = coroutineScope {
        do {
            val msg = channel.receive()
            val station = msg.getCurrentStation()

            if (!stationCommuters.contains(station)) stationCommuters[station] = mutableListOf()
            stationCommuters[station]!!.add(msg)
        } while (true)
    }

    private suspend fun embark(journey: Pair<String, String>, carriageChannel: Channel<Commuter>) = coroutineScope {
        val station = journey.second.substringAfter(":")
        do {
            //TODO review this.  ie platform checks...probably correct.
            stationCommuters[station]?.filter { it.peekNextJourneyStage().first == journey.second }?.forEach {
                stationCommuters[station]!!.remove(it)
                launch { carriageChannel.send(it) }
            }
            delay(timestep)
        } while (true)
    }
}