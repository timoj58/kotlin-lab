package com.tabiiki.kotlinlab.service

import com.tabiiki.kotlinlab.model.Transport
import kotlinx.coroutines.channels.Channel
import java.util.concurrent.ConcurrentHashMap

private class Queues {
    val queues: ConcurrentHashMap<Pair<String, String>, Pair<Channel<Transport>, ArrayDeque<Transport>>> =
        ConcurrentHashMap()

    fun getQueue(key: Pair<String, String>): ArrayDeque<Transport> = queues[key]!!.second

    fun initQueues(key: Pair<String, String>) {
        queues[key] = Pair(Channel(), ArrayDeque())
    }

    fun isQueueClear(section: Pair<String, String>): Boolean =
        queues[section]!!.second.isEmpty()

    fun getQueueKeys(): List<Pair<String, String>> = queues.keys().toList()

    fun release(key: Pair<String, String>, transport: Transport) {
        val limit = 1
        if (queues[key]!!.second.size > limit) throw RuntimeException("${transport.id} Only $limit transporters ${queues[key]!!.second.map { it.id }} allowed in $key")
        queues[key]!!.second.addLast(transport)
    }

    fun getChannel(key: Pair<String, String>): Channel<Transport> = queues[key]!!.first
}
