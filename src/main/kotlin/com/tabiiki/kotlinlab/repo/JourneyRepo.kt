package com.tabiiki.kotlinlab.repo

import org.springframework.stereotype.Repository
import java.util.concurrent.ConcurrentHashMap

interface JourneyRepo {
    fun addJourneyTime(journeyTime: Pair<Pair<String, String>, Int>)
}

@Repository
class JourneyRepoImpl : JourneyRepo {
    private val journeyTimes: ConcurrentHashMap<Pair<String, String>, Pair<Int, Int>> = ConcurrentHashMap()

    override fun addJourneyTime(journeyTime: Pair<Pair<String, String>, Int>) {
        if (!journeyTimes.containsKey(journeyTime.first)) journeyTimes[journeyTime.first] = Pair(0, 0)
        if (journeyTime.second != 0) {
            val stats = journeyTimes[journeyTime.first]
            journeyTimes[journeyTime.first] = Pair(stats!!.first + 1, stats.second + journeyTime.second)
        }
    }
}
