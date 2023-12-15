package com.tabiiki.kotlinlab.model

import com.tabiiki.kotlinlab.factory.AvailableRoute
import com.tabiiki.kotlinlab.service.RouteEnquiry
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.function.Consumer

/*
  need to review this class at some point, what do we really want it to do?
 */

data class Commuter(
    val id: UUID = UUID.randomUUID(),
    val commute: Pair<Pair<String, String>, Channel<RouteEnquiry>>,
    val timeStep: Long,
    val ready: Consumer<Commuter>
) {

    val channel: Channel<AvailableRoute> = Channel()
    private val route: MutableList<AvailableRoute> = mutableListOf()
    private val history: MutableList<Pair<String, String>> = mutableListOf()

    fun peekNextJourneyStage(): Pair<String, String>? = route.first().route.firstOrNull()

    fun completeJourneyStage() {
        val travelled = route.first().route.removeFirstOrNull() ?: throw Exception("route is already complete")
        history.add(travelled)
        println("commuter completed $travelled")
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
        } while (true)
    }
}
