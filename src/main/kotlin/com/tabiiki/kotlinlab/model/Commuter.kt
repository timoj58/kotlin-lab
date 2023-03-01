package com.tabiiki.kotlinlab.model

import com.tabiiki.kotlinlab.factory.AvailableRoute
import com.tabiiki.kotlinlab.service.RouteEnquiry
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.function.Consumer

data class Commuter(
    val id: UUID = UUID.randomUUID(),
    val commute: Pair<Pair<String, String>, Channel<RouteEnquiry>>, //TODO destroy commuter once completed?  makes sense.
    val timeStep: Long,
    val ready: Consumer<Commuter>,
) {

    val channel: Channel<AvailableRoute> = Channel()
    private val route: MutableList<AvailableRoute> = mutableListOf()
    private val history: MutableList<Pair<String, String>> = mutableListOf()

    fun peekNextJourneyStage(): Pair<String, String>? = route.first().route.firstOrNull()

    fun completeJourneyStage() {
        val travelled = route.first().route.removeFirstOrNull() ?: throw Exception("route is already complete")
        history.add(travelled)
        peekNextJourneyStage()?.let {
            println("next $it, completed $history for $id")
        } ?: println("completed $history for $id")
    }

    fun getCurrentStation(): String = Line.getStation(peekNextJourneyStage()?.first ?: commute.first.second)

    suspend fun initJourney() = coroutineScope {
        launch {
            commute.second.send(
                RouteEnquiry(route = commute.first, channel = channel)
            )
        }

        do {
            val enquiry = channel.receive()
            if (enquiry.route.isEmpty()) throw RuntimeException("no routes for $commute")
            route.add(enquiry)
            if (route.size == 1) ready.accept(this@Commuter)
        } while (true) //this needs to exit as well. TODO

    }

    companion object {

        /*  taken from old routeFactory.  of some use when working out which transporter to get on  ...

          fun getSublist(from: String, to: String, stations: List<String>): List<String> {
            val fromIdx = stations.indexOf(from)
            val toIdx = stations.indexOf(to)
            val fromCount = stations.count { it == from }
            val toCount = stations.count { it == to }

            return when (fromCount + toCount) {
                2 -> if (fromIdx < toIdx) stations.subList(fromIdx, toIdx + 1) else stations.subList(toIdx, fromIdx + 1)
                    .reversed()
                //circle line  418 to 418 .. return the shortest route
                3, 4 -> getLeastStops(from, to, stations)
                else -> throw RuntimeException("invalid station $from $to count on route $fromCount + $toCount $stations")
            }
        }

        private fun getLeastStops(from: String, to: String, stations: List<String>): List<String> {
            //for each index of either, find the shortest distance...fixed on basis only one line has two stations.  circle.
            val fromFirstIdx = stations.indexOfFirst { it == from }
            val fromLastIdx = stations.indexOfLast { it == from }
            val toFirstIdx = stations.indexOfFirst { it == to }
            val toLastIdx = stations.indexOfLast { it == to }

            return if (toFirstIdx == toLastIdx)
                calcShortestRoute(fromFirstIdx, fromLastIdx, toFirstIdx, stations) else
                calcShortestRoute(toFirstIdx, toLastIdx, fromFirstIdx, stations).reversed()
        }

        private fun calcShortestRoute(idx1a: Int, idx1b: Int, idx2: Int, stations: List<String>): List<String> {
            val possibleRoutes = mutableListOf<List<String>>()

            if (idx2 > idx1a) possibleRoutes.add(stations.subList(idx1a, idx2 + 1))
            if (idx2 > idx1b) possibleRoutes.add(stations.subList(idx1b, idx2 + 1))
            if (idx2 < idx1a) possibleRoutes.add(stations.subList(idx2, idx1a + 1).reversed())
            if (idx2 < idx1b) possibleRoutes.add(stations.subList(idx2, idx1b + 1).reversed())

            return possibleRoutes.minBy { it.size }
        }

         */


    }
}