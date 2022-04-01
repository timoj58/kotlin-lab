package com.tabiiki.kotlinlab.service

import com.tabiiki.kotlinlab.model.Transport
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap

interface LineSectionService {
    fun enterSection(transport: Transport, lineStations: List<String>)
    fun getNext(section: Pair<String, String>): Pair<Transport, List<String>>?
}

@Service
class LineSectionServiceImpl : LineSectionService {

    private val sectionQueues: ConcurrentHashMap<Pair<String, String>, ArrayDeque<Pair<Transport, List<String>>>> =
        ConcurrentHashMap()

    override fun enterSection(transport: Transport, lineStations: List<String>) {
        if (!sectionQueues.containsKey(transport.linePosition)) sectionQueues[transport.linePosition] = ArrayDeque()
        sectionQueues[transport.linePosition]!!.addLast(Pair(transport, lineStations))
    }

    override fun getNext(section: Pair<String, String>): Pair<Transport, List<String>>? {
        return if (!sectionQueues.containsKey(section)) null else sectionQueues[section]!!.removeFirstOrNull()
    }
}