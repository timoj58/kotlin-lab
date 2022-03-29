package com.tabiiki.kotlinlab.service

import com.tabiiki.kotlinlab.model.Transport
import kotlinx.coroutines.channels.Channel
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.*


interface StationService {
    fun getDepartures(id: String): Set<UUID>
    fun getArrivals(id: String): Set<UUID>
    suspend fun monitor(id: String, channel: Channel<Transport>)
}

@Service
class StationServiceImpl(stationsService: StationsService): StationService {

    private val log = LoggerFactory.getLogger(this.javaClass)

    private val departures = stationsService.get().map { it.id }.associateWith { mutableSetOf<UUID>() }
    private val arrivals = stationsService.get().map { it.id }.associateWith { mutableSetOf<UUID>() }

    override fun getDepartures(id: String): Set<UUID> {
        return departures[id]!!
    }

    override fun getArrivals(id: String): Set<UUID> {
        return arrivals[id]!!
    }

    override suspend fun monitor(id: String, channel: Channel<Transport>){
        do {
            val message = channel.receive()
            if(!message.isStationary() && message.linePosition.second == id ){
                arrivals[id]?.add(message.id)
                departures[message.linePosition.first]?.remove(message.id)
            }else if (message.isStationary() && message.linePosition.first == id){
                departures[id]?.add(message.id)
                arrivals[message.linePosition.second]?.remove(message.id)
            }
        }while (true)
    }

}