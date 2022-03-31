package com.tabiiki.kotlinlab.repo

import com.tabiiki.kotlinlab.model.Status
import com.tabiiki.kotlinlab.model.Transport
import kotlinx.coroutines.channels.Channel
import org.springframework.stereotype.Repository
import java.util.*
import java.util.concurrent.ConcurrentHashMap


private data class TransporterTracker(
    val id: UUID,
    val position: Pair<String, String>,
    val status: Status,
    val journeyTime: Int)

interface TransporterTrackerRepo{
    fun isSectionClear(transport: Transport): Boolean
    suspend fun track(channel: Channel<Transport>)
}

@Repository
class TransporterTrackerRepoImpl: TransporterTrackerRepo {

    private var repo: ConcurrentHashMap<UUID, TransporterTracker> = ConcurrentHashMap()
    override fun isSectionClear(transport: Transport): Boolean =
        if (repo.isEmpty()) true else repo.values.none { it.id != transport.id && it.status != Status.PLATFORM && it.position == transport.linePosition}

    override suspend fun track(channel: Channel<Transport>) {
        do {
            val msg = channel.receive()
            repo[msg.id] = TransporterTracker(
                id = msg.id,
                position = msg.linePosition,
                status = msg.status,
                journeyTime = msg.getJourneyTime().first
            )
        }while (true)
    }

}