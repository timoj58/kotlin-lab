package com.tabiiki.kotlinlab.model

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import java.util.UUID

data class Commuter(
    val id: UUID = UUID.randomUUID(),
    val commute: Pair<String, String>,
    val channel: Channel<Commuter>,
    val timeStep: Long,
) {
    private var route: List<Pair<String, String>> = mutableListOf()

    fun journey(): Pair<String, String> {
        TODO("return the latest stage of journey")
    }

    fun station(): String {
        TODO("return the station - handle initial action")
    }

    suspend fun track() {
        do {
             channel.send(this)
             delay(timeStep)
        } while ( route.none { it.second == commute.second })
    }

    companion object {
        //TODO finding the routes, probably should be dynamic. static first cut.
        class RouteCalculator {
            /*
               to calculate a route, the commuter needs to know all the lines, and stations.
               ie, given x & y, what options are available.

               notes here.

               as part of this, may need to change on the same line, if its a train that terminates early.

               perhaps build into the signalling.  ie train X arrives with destination.
               ie dont get on.  or do and get off at end.  computer less stupid than a human i guess.

             */

        }
    }
}