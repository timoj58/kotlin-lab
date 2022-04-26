package com.tabiiki.kotlinlab.repo

import com.tabiiki.kotlinlab.util.LineBuilder
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

@Disabled
internal class JourneyRepoTest {

    private val journeyTimeRepoImpl = JourneyRepoImpl()
    private val line = LineBuilder().getLine()

    @Test
    fun `journey time is greater than holding delay`() {
        journeyTimeRepoImpl.addJourneyTime(Pair(Pair("A", "B"), 100))
        val train = line.transporters.first()
        train.addSection(Pair("A", "B"))
        //     assertThat(journeyTimeRepoImpl.isJourneyTimeGreaterThanHoldingDelay(listOf(line), train)).isGreaterThan(0)
    }

    @Test
    fun `journey time is less than holding delay`() {
        journeyTimeRepoImpl.addJourneyTime(Pair(Pair("B", "C"), 10))
        val train = line.transporters.first()
        train.addSection(Pair("B", "C"))
        //     assertThat(journeyTimeRepoImpl.isJourneyTimeGreaterThanHoldingDelay(listOf(line), train)).isLessThan(0)

    }

    @Test
    fun `journey time does not exist`() {
        val train = line.transporters.first()
        train.addSection(Pair("X", "Y"))
        //     assertThat(journeyTimeRepoImpl.isJourneyTimeGreaterThanHoldingDelay(listOf(line), train)).isEqualTo(0)

    }

    @Test
    fun `journey time is zero and therefore doesnt exist test`() {
        journeyTimeRepoImpl.addJourneyTime(Pair(Pair("B", "C"), 0))
        val train = line.transporters.first()
        train.addSection(Pair("B", "C"))
        //    assertThat(journeyTimeRepoImpl.isJourneyTimeGreaterThanHoldingDelay(listOf(line), train)).isEqualTo(0)
    }

}