package com.tabiiki.kotlinlab.repo

import com.tabiiki.kotlinlab.util.LineBuilder
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class JourneyRepoTest {

    private val journeyTimeRepoImpl = JourneyRepoImpl()
    private val line = LineBuilder().getLine()

    @Test
    fun `journey time is greater than holding delay`() {
        journeyTimeRepoImpl.addJourneyTime(Pair(100, Pair("A", "B")))
        val train = line.transporters.first()
        train.linePosition = Pair("A", "B")
        assertThat(journeyTimeRepoImpl.isJourneyTimeGreaterThanHoldingDelay(listOf(line), train)).isEqualTo(true)
    }

    @Test
    fun `journey time is less than holding delay`() {
        journeyTimeRepoImpl.addJourneyTime(Pair(10, Pair("B", "C")))
        val train = line.transporters.first()
        train.linePosition = Pair("B", "C")
        assertThat(journeyTimeRepoImpl.isJourneyTimeGreaterThanHoldingDelay(listOf(line), train)).isEqualTo(false)

    }

    @Test
    fun `journey time does not exist`() {
        val train = line.transporters.first()
        train.linePosition = Pair("X", "Y")
        assertThat(journeyTimeRepoImpl.isJourneyTimeGreaterThanHoldingDelay(listOf(line), train)).isEqualTo(false)

    }

    @Test
    fun `journey time is zero and therefore doesnt exist test`() {
        journeyTimeRepoImpl.addJourneyTime(Pair(0, Pair("B", "C")))
        val train = line.transporters.first()
        train.linePosition = Pair("B", "C")
        assertThat(journeyTimeRepoImpl.isJourneyTimeGreaterThanHoldingDelay(listOf(line), train)).isEqualTo(false)
    }

}