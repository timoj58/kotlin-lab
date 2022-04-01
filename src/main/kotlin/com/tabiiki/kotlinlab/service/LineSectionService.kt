package com.tabiiki.kotlinlab.service

import com.tabiiki.kotlinlab.factory.SignalFactory
import com.tabiiki.kotlinlab.factory.SignalValue
import com.tabiiki.kotlinlab.model.Transport
import org.springframework.stereotype.Service

interface LineSectionService {
    suspend fun enterSection(transport: Transport, lineStations: List<String>)
    fun getNext(section: Pair<String, String>): Pair<Transport, List<String>>?
}

@Service
class LineSectionServiceImpl(
    private val signalFactory: SignalFactory
) : LineSectionService {

   // private val sectionQueues: ConcurrentHashMap<Pair<String, String>, ArrayDeque<Pair<Transport, List<String>>>> = ConcurrentHashMap()

    override suspend fun enterSection(transport: Transport, lineStations: List<String>) {
    //    signalService.notify(section = transport.section, status = SignalValue.AMBER)
  //      if (!sectionQueues.containsKey(transport.section)) sectionQueues[transport.section] = ArrayDeque()
  //      sectionQueues[transport.section]!!.addLast(Pair(transport, lineStations))
    }

    override fun getNext(section: Pair<String, String>): Pair<Transport, List<String>>? {
        return null
   //     return if (!sectionQueues.containsKey(section)) null else sectionQueues[section]!!.removeFirstOrNull()
    }
}