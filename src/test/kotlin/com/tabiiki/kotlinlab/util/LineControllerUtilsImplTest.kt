package com.tabiiki.kotlinlab.util

import com.tabiiki.kotlinlab.configuration.LineConfig
import com.tabiiki.kotlinlab.configuration.TransportConfig
import com.tabiiki.kotlinlab.model.Line
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

internal class LineControllerUtilsImplTest{

    private val lineControllerUtilsImpl = LineControllerUtilsImpl()

    private val transportConfig =
        TransportConfig(transportId = 1, capacity = 100, weight = 1000, topSpeed = 75, power = 100)

    private val line = Line(
        LineConfig(
            id = "1",
            name = "2",
            transportId = 1,
            holdDelay = 45,
            transportCapacity = 8,
            stations = listOf("A", "B", "C"),
            depots = listOf("A", "C")
        ), listOf(transportConfig)
    )


    @Test
    fun `journey time is greater than holding delay`(){
        lineControllerUtilsImpl.addJourneyTime(Pair("A", "B"), 100)
        val train = line.transporters.first()
        train.linePosition = Pair("A", "B")
        assertThat(lineControllerUtilsImpl.isJourneyTimeGreaterThanHoldingDelay(listOf(line),train)).isEqualTo(true)
    }

    @Test
    fun `journey time is less than holding delay`(){
        lineControllerUtilsImpl.addJourneyTime(Pair("B", "C"), 10)
        val train = line.transporters.first()
        train.linePosition = Pair("B", "C")
        assertThat(lineControllerUtilsImpl.isJourneyTimeGreaterThanHoldingDelay(listOf(line),train)).isEqualTo(false)

    }

    @Test
    fun `journey time does not exist`(){
        val train = line.transporters.first()
        train.linePosition = Pair("X", "Y")
        assertThat(lineControllerUtilsImpl.isJourneyTimeGreaterThanHoldingDelay(listOf(line),train)).isEqualTo(false)

    }

}