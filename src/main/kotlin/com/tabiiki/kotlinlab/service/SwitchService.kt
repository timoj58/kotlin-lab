package com.tabiiki.kotlinlab.service

import com.tabiiki.kotlinlab.factory.LineFactory
import com.tabiiki.kotlinlab.model.Transport
import org.springframework.stereotype.Service
import java.util.function.Consumer

interface SwitchService {
    fun isSwitchSection(transport: Transport): Boolean
    suspend fun switch(transport: Transport, completeSection: Consumer<Pair<Transport, Pair<String, String>>>)
}

@Service
class SwitchServiceImpl(
    private val lineFactory: LineFactory
): SwitchService {
    override fun isSwitchSection(transport: Transport): Boolean {
        val section = Pair(transport.section().first.substringAfter(":"), transport.section().second)
        val isPossibleSwitch = lineFactory.isSwitchSection(transport.line.name, section)
        if(!isPossibleSwitch) return false

        val firstStation = transport.line.stations.first()
        val lastStation = transport.line.stations.last()

        return listOf(firstStation, lastStation).any { it == section.first || it == section.second }
    }

    override suspend fun switch(transport: Transport, completeSection: Consumer<Pair<Transport, Pair<String, String>>>) {
        //loop.  monitor where it is, at a (to be determined) distance switch track...call consumer.
        /*
           before this....


           is

         */
        TODO("Not yet implemented ${transport.section()}")
    }

}