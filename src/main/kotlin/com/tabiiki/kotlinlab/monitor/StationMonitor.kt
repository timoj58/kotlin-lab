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

class StationMonitor(val timeStep: Long, stations: List<String>) {

    private val stationCommuters: ConcurrentHashMap<String, MutableList<Commuter>> = ConcurrentHashMap()
    private val carriageChannelJobs: ConcurrentHashMap<UUID, Job> = ConcurrentHashMap()

    init {
        stations.forEach { stationCommuters[it] = mutableListOf() }
    }

    suspend fun monitorPlatform(
        platformChannel: Channel<SignalMessage>,
        stationChannel: Channel<SignalMessage>,
    ) = coroutineScope {
        var previousSignal: SignalValue? = null
        do {
            val msg = platformChannel.receive()
            if(msg.commuterChannel == null && !msg.init) throw Exception("no channel from ${msg.producer}")
            msg.commuterChannel?.let {
                when (msg.signalValue) {
                    SignalValue.RED -> carriageChannelJobs[msg.id!!] = launch { embark(msg.key!!, it) }
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
                            transportId = it,
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
            stationCommuters[station]?.add(msg) ?: throw Exception("missing channel for $station")
        } while (true)
    }

    private suspend fun embark(journey: Pair<String, String>, carriageChannel: Channel<Commuter>) = coroutineScope {
        val station = journey.second.substringAfter(":")
        do {
            stationCommuters[station]?.let {
                it.filter { commuter ->  commuter.peekNextJourneyStage().first == journey.second }.forEach { embark ->
                    stationCommuters[station]!!.remove(embark)
                    launch { carriageChannel.send(embark) }
                }
            }
            delay(timeStep)
        } while (true)
    }
}