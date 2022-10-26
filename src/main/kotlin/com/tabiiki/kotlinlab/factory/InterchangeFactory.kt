package com.tabiiki.kotlinlab.factory

import com.tabiiki.kotlinlab.model.Line
import org.springframework.stereotype.Component

@Component
class InterchangeFactory(
    private val lineFactory: LineFactory
) {
    final val lines: List<Line> = lineFactory.get().map { lineFactory.get(it) }
    final val stations = lines.map { it.stations }.flatten()

    //TODO there will be a cost to this in terms of startup.  should be in init method.
    final val interchanges = generateInterchanges(lines)
    final val virtualInterchanges = generateVirtualInterchanges(lines)

    fun getLines(include: String, exclude: String): List<Line> =
        lines.filter { it.stations.contains(include) && it.stations.none { s -> s == exclude } }.filter {
            it.stations.any { station -> interchanges.contains(station) }
        }

    fun filterLinesToTest(lineToTest: Line, interchange: String, testedLines: List<String>?): List<Line> =
        lines.filter { line ->
            line.id != lineToTest.id && line.stations.any {
                interchange == it && testedLines?.none { t -> t == line.id } ?: true
            }
        }

    fun linesToTest(interchange: String) =
        lines.filter { line -> line.stations.any { interchange == it } }.toMutableList()

    private fun generateInterchanges(lines: List<Line>): List<String> {
        val interchanges = mutableListOf<String>()
        lines.forEach { line ->
            val otherLines = lines.filter { it.name != line.name }
            line.stations.forEach { station ->
                otherLines.forEach { other ->
                    if (other.stations.contains(station)) interchanges.add(station)
                }
            }
        }

        return interchanges.distinct()
    }

    private fun generateVirtualInterchanges(lines: List<Line>): List<String> {
        TODO("implement this")
    }
}