package com.tabiiki.kotlinlab.factory

import com.tabiiki.kotlinlab.configuration.StationConfig
import com.tabiiki.kotlinlab.configuration.StationsConfig
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.mockito.Mockito


internal class StationFactoryTest {

    private val stationsConfig = Mockito.mock(StationsConfig::class.java)

    @Test
    fun `get station`() {
        Mockito.`when`(stationsConfig.stations).thenReturn(
            listOf(
                StationConfig(id = "A"),
                StationConfig(id = "B"),
                StationConfig(id = "C"),
            )
        )

        val station = StationFactory(stationsConfig).get("A")
        assertNotNull(station)
    }

    @Test
    fun `station does not exist`() {
        Mockito.`when`(stationsConfig.stations).thenReturn(listOf())
        Assertions.assertThrows(NoSuchElementException::class.java) {
            StationFactory(stationsConfig).get("A")
        }
    }

    @ParameterizedTest
    @CsvSource(
        "30|10|20|40|50,2,80",
        "30|10|20|40|50,3,90",
        "10|20|70|60|40|20|10|90|70,4,180",
        "90|70|50|10|30|10|20|30|90|100|25|35|22|40,6,260",
        "10|10|10|10|10,3,30",
        "50|40|30|20|10,3,80"
    )
    fun `booking test`(complexity: String, days: Int, result: Int) {
        val complexityAsList = complexity.split("|").map { it.toInt() }.toMutableList()
        if (complexityAsList.isEmpty() || complexityAsList.size < days) throw Exception("invalid complexity")
        if (days == 0) throw Exception("invalid days")
        var smallest = complexityAsList.min()
        val optimisedDaysComplexity = mutableListOf<MutableList<Int>>()
        optimisedDaysComplexity.add(complexityAsList)

        while (optimisedDaysComplexity.size < days) {
            if (optimisedDaysComplexity.filter { it.contains(smallest) }.all { it.size == 1 })
                smallest = complexityAsList.filter { it != smallest }.min()
            val located =
                optimisedDaysComplexity.filter { it.size > 1 && it.contains(smallest) }.toMutableList().first()
            optimisedDaysComplexity.remove(located)
            val index = located.indexOfFirst { it == smallest }
            val actualIndex = if (index == 1) 1 else if (index == located.size - 1) index else index + 1

            optimisedDaysComplexity.addAll(
                mutableListOf(
                    located.subList(0, actualIndex),
                    located.subList(actualIndex, located.size)
                )
            )
        }

        assert(optimisedDaysComplexity.sumOf { it.max() } == result)
    }

    suspend fun job () {

    }

    @Test
    fun `why doesnt job cancel test` () {

    }
}