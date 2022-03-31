package com.tabiiki.kotlinlab.repo

import com.tabiiki.kotlinlab.model.Line
import com.tabiiki.kotlinlab.model.Status
import com.tabiiki.kotlinlab.model.Transport
import org.springframework.stereotype.Repository
import java.util.*

interface JourneyRepo {
    fun isJourneyTimeGreaterThanHoldingDelay(line: List<Line>, transport: Transport): Int
    fun getDefaultHoldDelay(line: List<Line>, id: UUID): Int
    fun addJourneyTime(journeyTime: Pair<Int, Pair<String, String>>)
}

@Repository
class JourneyRepoImpl : JourneyRepo {
    private val journeyTimes = mutableMapOf<Pair<String, String>, Int>()

    override fun isJourneyTimeGreaterThanHoldingDelay(line: List<Line>, transport: Transport) =
        if (!journeyTimes.containsKey(transport.linePosition)) 0
        else journeyTimes[transport.linePosition]!! - getDefaultHoldDelay(line, transport.id)

    override fun getDefaultHoldDelay(line: List<Line>, id: UUID): Int =
        line.first { l -> l.transporters.any { it.id == id } }.holdDelay

    override fun addJourneyTime(journeyTime: Pair<Int, Pair<String, String>>) {
        if (journeyTime.first != 0) journeyTimes[journeyTime.second] = journeyTime.first
    }
}
