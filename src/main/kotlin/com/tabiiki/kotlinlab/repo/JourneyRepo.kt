package com.tabiiki.kotlinlab.util

import com.tabiiki.kotlinlab.model.Line
import com.tabiiki.kotlinlab.model.Transport
import org.springframework.stereotype.Repository
import java.util.*

interface JourneyRepo {
    fun isJourneyTimeGreaterThanHoldingDelay(line: List<Line>, transport: Transport): Boolean
    fun isLineSegmentClear(section: Line, transport: Transport): Boolean
    fun getDefaultHoldDelay(line: List<Line>, id: UUID): Int
    fun addJourneyTime(journeyTime: Pair<Int, Pair<String, String>>)
}

@Repository
class JourneyRepoImpl : JourneyRepo {
    private val journeyTimes = mutableMapOf<Pair<String, String>, Int>()

    override fun isJourneyTimeGreaterThanHoldingDelay(line: List<Line>, transport: Transport) =
        if (!journeyTimes.containsKey(transport.linePosition)) false
        else journeyTimes[transport.linePosition]!! > getDefaultHoldDelay(line, transport.id)

    override fun isLineSegmentClear(section: Line, transport: Transport) =
        section.transporters.filter { it.id != transport.id }.all { it.linePosition != transport.linePosition }

    override fun getDefaultHoldDelay(line: List<Line>, id: UUID): Int =
        line.first { l -> l.transporters.any { it.id == id } }.holdDelay

    override fun addJourneyTime(journeyTime: Pair<Int, Pair<String, String>>) {
        if (journeyTime.first != 0) journeyTimes[journeyTime.second] = journeyTime.first
    }
}
