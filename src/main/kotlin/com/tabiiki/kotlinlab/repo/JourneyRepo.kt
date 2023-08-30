package com.tabiiki.kotlinlab.repo

import org.springframework.stereotype.Repository
import java.util.concurrent.ConcurrentHashMap

interface JourneyRepo {
    fun addJourneyTime(journeyTime: Triple<Pair<String, String>, Int, Double>)
    fun getJourneyTime(section: Pair<String, String>, missing: Int): Pair<Int, Double>
}

@Repository
class JourneyRepoImpl : JourneyRepo {
    private val journeyTimes: ConcurrentHashMap<Pair<String, String>, Triple<Int, Int, Double>> = ConcurrentHashMap()

    override fun addJourneyTime(journeyTime: Triple<Pair<String, String>, Int, Double>) {
        if (!journeyTimes.containsKey(journeyTime.first)) journeyTimes[journeyTime.first] = Triple(0, 0, 0.0)
        if (journeyTime.second != 0) {
            val stats = journeyTimes[journeyTime.first]
            journeyTimes[journeyTime.first] =
                Triple(stats!!.first + 1, stats.second + journeyTime.second, journeyTime.third)
        }
    }

    override fun getJourneyTime(section: Pair<String, String>, missing: Int): Pair<Int, Double> {
        if (!journeyTimes.containsKey(section)) return Pair(missing, 0.0)
        val journey = journeyTimes[section]!!
        return Pair(journey.second / journey.first, journey.third)
    }
}
