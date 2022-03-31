package com.tabiiki.kotlinlab.repo

import org.springframework.stereotype.Repository
import java.util.concurrent.ConcurrentHashMap

interface JourneyRepo {
    fun addJourneyTime(journeyTime: Pair<Int, Pair<String, String>>)
}

@Repository
class JourneyRepoImpl : JourneyRepo {
    private val journeyTimes: ConcurrentHashMap<Pair<String, String>, Pair<Int, Int>> = ConcurrentHashMap()

    override fun addJourneyTime(journeyTime: Pair<Int, Pair<String, String>>) {
        if (!journeyTimes.containsKey(journeyTime.second)) journeyTimes[journeyTime.second] = Pair(0,0)
        if (journeyTime.first != 0){
            val stats = journeyTimes[journeyTime.second]
            journeyTimes[journeyTime.second] = Pair(stats!!.first+1, stats.second+journeyTime.first)
        }
    }
}
