package com.tabiiki.kotlinlab.repo

import com.tabiiki.kotlinlab.model.Status
import com.tabiiki.kotlinlab.model.Transport
import com.tabiiki.kotlinlab.util.LineBuilder
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class TransporterTrackerRepoTest{

    private val transporterTrackerRepo = TransporterTrackerRepoImpl()

    @Test
    fun `track is clear `() = runBlocking{

        val channel = Channel<Transport>()
        val job = async { transporterTrackerRepo.track(channel) }

        assertThat(transporterTrackerRepo.isSectionClear(
            Transport(
                config = LineBuilder().transportConfig,
                lineId = "1",
                timeStep = 10L
            )
        )).isEqualTo(true)

        job.cancelAndJoin()
    }

    @Test
    fun `track is not clear` () = runBlocking{
        val channel = Channel<Transport>()
        val transport = Transport(config = LineBuilder().transportConfig, lineId = "1", timeStep = 10).also {
            it.status = Status.ACTIVE
            it.linePosition = Pair("A", "B")
        }
        val job = async { transporterTrackerRepo.track(channel) }
        channel.send(transport)

        assertThat(transporterTrackerRepo.isSectionClear(
            Transport(
                config = LineBuilder().transportConfig,
                lineId = "1",
                timeStep = 10L
            ).apply { this.linePosition = Pair("A", "B") }
        )).isEqualTo(false)

        job.cancelAndJoin()

    }


}