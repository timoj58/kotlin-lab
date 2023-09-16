package com.tabiiki.kotlinlab.repo

import com.tabiiki.kotlinlab.util.LineBuilder
import org.junit.jupiter.api.Test

internal class JourneyRepoTest {

    private val journeyTimeRepoImpl = JourneyRepo()
    private val line = LineBuilder().getLine()

    @Test
    fun `journey time is greater than holding delay`() {
        journeyTimeRepoImpl.addJourneyTime(Triple(Pair("A", "B"), 100, 0.0))
        val train = line.transporters.first()
        train.addSection(Pair("1:A", "B"))
        //     assertThat(journeyTimeRepoImpl.isJourneyTimeGreaterThanHoldingDelay(listOf(line), train)).isGreaterThan(0)
    }

    @Test
    fun `journey time is less than holding delay`() {
        journeyTimeRepoImpl.addJourneyTime(Triple(Pair("B", "C"), 10, 0.0))
        val train = line.transporters.first()
        train.addSection(Pair("1:B", "C"))
        //     assertThat(journeyTimeRepoImpl.isJourneyTimeGreaterThanHoldingDelay(listOf(line), train)).isLessThan(0)
    }

    @Test
    fun `journey time does not exist`() {
        val train = line.transporters.first()
        train.addSection(Pair("1:X", "Y"))
        //     assertThat(journeyTimeRepoImpl.isJourneyTimeGreaterThanHoldingDelay(listOf(line), train)).isEqualTo(0)
    }

    @Test
    fun `journey time is zero and therefore doesnt exist test`() {
        journeyTimeRepoImpl.addJourneyTime(Triple(Pair("B", "C"), 0, 0.0))
        val train = line.transporters.first()
        train.addSection(Pair("1:B", "C"))
        //    assertThat(journeyTimeRepoImpl.isJourneyTimeGreaterThanHoldingDelay(listOf(line), train)).isEqualTo(0)
    }
}
