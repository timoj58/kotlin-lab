package com.tabiiki.kotlinlab.model

import com.tabiiki.kotlinlab.factory.AvailableRoutes
import com.tabiiki.kotlinlab.service.RouteEnquiry
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.function.Consumer

data class Commuter(
    val id: UUID = UUID.randomUUID(),
    val commute: Pair<String, String>, //TODO destroy commuter once completed?  makes sense.
    val stationChannel: Channel<Commuter>,
    val timeStep: Long,
    val routeChannel: Channel<RouteEnquiry>,
    val ready: Consumer<Commuter>,
) {

    fun getNextJourneyStage(): Pair<String, String> =
        route!!.removeFirstOrNull() ?: throw Exception("route is complete")

    fun getCurrentStation(): String = commute.first

    suspend fun initJourney() = coroutineScope {
        launch {
            routeChannel.send(
                RouteEnquiry(route = commute, channel = channel)
            )
        }

        val enquiry = channel.receive()
        route = enquiry.routes.minBy { it.size }.toMutableList()
        ready.accept(this@Commuter)
    }

    suspend fun track() {
        do {
            stationChannel.send(this)
            delay(timeStep)
        } while (route!!.isNotEmpty())
    }

    companion object {
        val channel: Channel<AvailableRoutes> = Channel()
        var route: MutableList<Pair<String, String>>? = null
    }
}