package com.tabiiki.kotlinlab.model

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import java.util.UUID

data class Commuter(
    val id: UUID = UUID.randomUUID(),
    val commute: Pair<String, String>,
    val channel: Channel<Commuter>,
    val timeStep: Long
) {
    //this can be calculated dynamically? need to track though....
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
        } while (true)
    }

    companion object {
        //something like this for now.
        class RouteCalculator {

        }
    }
}