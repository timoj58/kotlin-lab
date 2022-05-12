package com.tabiiki.kotlinlab.service

import com.tabiiki.kotlinlab.factory.SignalMessage
import com.tabiiki.kotlinlab.factory.SignalValue
import com.tabiiki.kotlinlab.model.Transport
import com.tabiiki.kotlinlab.repo.JourneyRepo
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

interface SectionService {
    suspend fun add(transport: Transport, channel: Channel<Transport>)
    suspend fun init()
    fun isClear(key: Pair<String, String>): Boolean
    fun initQueues(key: Pair<String, String>)
    fun dump()
}

@Service
class SectionServiceImpl(
    @Value("\${network.minimum-hold}") minimumHold: Int,
    private val signalService: SignalService,
    private val journeyRepo: JourneyRepo
) : SectionService {

    private val queues = Queues(minimumHold)
    private val diagnostics = Diagnostics()

    override suspend fun add(transport: Transport, channel: Channel<Transport>): Unit = coroutineScope {
        holdChannels[transport.id] = channel
        queues.release(transport.section(), transport)
        jobs[transport.id] = launch {
            transport.track(queues.getChannel(transport.section()))
        }

        launch { transport.signal(signalService.getChannel(transport.section())!!) }
    }

    override suspend fun init(): Unit = coroutineScope {
        queues.getQueueKeys().forEach {
            launch { signalService.init(it) }
            launch { monitor(it) }
        }
    }

    override fun isClear(key: Pair<String, String>): Boolean = queues.isClear(key)
    override fun initQueues(key: Pair<String, String>) {
        queues.initQueues(key)

    }

    override fun dump() {
        diagnostics.dump(queues)
    }

    private suspend fun monitor(key: Pair<String, String>) = coroutineScope {
        val channel = queues.getChannel(key)
        do {
            val msg = channel.receive()
            when (msg.atPlatform()) {
                true -> {
                    queues.getQueue(key).removeFirstOrNull()?.let {
                        jobs[it.id]!!.cancelAndJoin()

                        val platformFromKey = msg.platformFromKey()
                        //TODO remove this once section handling set for all transporters.  yes remove this...
                        launch { signalService.send(platformFromKey, SignalMessage(SignalValue.GREEN)) }
                        launch { arrive(it) }
                    }
                }
                else -> {}
            }

        } while (true)
    }

    private suspend fun arrive(transport: Transport) = coroutineScope{
        transport.journal.add(
            Transport.Companion.JournalRecord(
                action = Transport.Companion.JournalActions.PLATFORM_HOLD,
                key = transport.platformToKey(),
                signal = SignalValue.RED
            )
        )
        journeyRepo.addJourneyTime(transport.getJourneyTime())
        holdChannels[transport.id]!!.send(transport)

    }

    companion object {
        private val log = LoggerFactory.getLogger(this.javaClass)
        private val jobs: ConcurrentHashMap<UUID, Job> = ConcurrentHashMap()
        private val holdChannels: ConcurrentHashMap<UUID, Channel<Transport>> = ConcurrentHashMap()

        class Queues(private val minimumHold: Int) {
            private val queues: ConcurrentHashMap<Pair<String, String>, Pair<Channel<Transport>, ArrayDeque<Transport>>> =
                ConcurrentHashMap()

            fun getQueue(key: Pair<String, String>): ArrayDeque<Transport> =
                queues[key]!!.second

            fun initQueues(key: Pair<String, String>) {
                queues[key] = Pair(Channel(), ArrayDeque())
            }

            fun getQueueKeys(): Iterator<Pair<String, String>> = queues.keys().asIterator()

            fun release(key: Pair<String, String>, transport: Transport) {
                queues[key]!!.second.add(transport)
                transport.journal.add(
                    Transport.Companion.JournalRecord(
                        action = Transport.Companion.JournalActions.RELEASE, key = key
                    )
                )
            }

            fun getChannel(key: Pair<String, String>): Channel<Transport> = queues[key]!!.first
            fun isClear(key: Pair<String, String>): Boolean {
                if (!queues.containsKey(key)) return false
                if (queues[key]!!.second.isEmpty()) return true
                return queues[key]!!.second.first().getJourneyTime().second > minimumHold
            }

        }

        class Diagnostics {

            fun dump(queues: Queues) {
                val items = mutableListOf<Transport>()

                queues.getQueueKeys().forEach {
                    items.addAll(queues.getQueue(it))
                }

                items.map { it.journal.getLog() }.flatten().sortedBy { it.milliseconds }
                    .forEach { log.info(it.print()) }
            }
        }
    }
}