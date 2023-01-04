package com.tabiiki.kotlinlab.factory

import com.tabiiki.kotlinlab.configuration.LineType
import com.tabiiki.kotlinlab.model.Line
import com.tabiiki.kotlinlab.util.HaversineCalculator
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

@Component
class InterchangeFactory(
    private val lineFactory: LineFactory,
    private val stationFactory: StationFactory,
) {
    final val lines: List<Line> = lineFactory.get().map { lineFactory.get(it) }
    final val stations = lines.map { it.stations }.flatten()
    private val links: ConcurrentHashMap<String, MutableSet<Pair<String, String>>> =
        ConcurrentHashMap()
    private val virtualLinks: ConcurrentHashMap<Pair<String, String>, MutableSet<Pair<String, String>>> =
        ConcurrentHashMap()

    init {
        lines.forEach { links[it.name] = mutableSetOf() }
        val interchangesWithLineTypes = generateInterchanges(lines)
        generateVirtualInterchanges(lines, interchangesWithLineTypes)
    }

    fun getLineType(line: String): LineType = lines.first { it.name == line }.getType()

    fun getLinks(key: String, lineType: LineType, exclude: String) = links[key]!!.filter { it.second != exclude }
        .sortedByDescending { getLineType(it.first) == lineType }

    fun isVirtualLink(link: Pair<String, String>) = virtualLinks.containsKey(link)

    fun getVirtualLinkTo(link: Pair<String, String>, line: String) =
        virtualLinks[link]?.firstOrNull { it.first == line }

    fun getLineIdsByLink(link: Pair<String, String>) =
        lines.filter { it.name == link.first }.filter { it.stations.contains(link.second) }.map { it.id }

    fun getLines(include: String, exclude: String): List<Line> =
        lines.filter { it.stations.contains(include) && it.stations.none { s -> s == exclude } }

    private fun generateInterchanges(lines: List<Line>): List<String> {
        val result = mutableListOf<String>()
        lines.forEach { line ->
            lines.filter { it.id != line.id }.forEach { other ->
                filterSharedLinedInterchanges(line, other).forEach { station ->
                    if (other.stations.contains(station)) {
                        result.add("${line.getType()}:$station")
                        links[line.name]!!.add(Pair(other.name, station))
                    }
                }
            }
        }

        return result.distinct()
    }

    private fun generateVirtualInterchanges(
        lines: List<Line>,
        interchangesWithLines: List<String>
    ) {
        val typesToTest = lines.filter { line ->
            line.stations.none { station ->
                line.getType().notThis().any { interchangesWithLines.contains("$it:$station") }
            }
        }.groupBy { it.getType() }

        val stationsToTest = typesToTest.values.asSequence().flatten().filter { line ->
            line.stations.none {
                interchangesWithLines.contains("${line.getType()}:$it")
                        && typesToTest[line.getType()]!!.size != lines.filter { l -> l.getType() == line.getType() }.size
            }
        }.map { it.stations.map { s -> "${it.name}:$s" }.toList() }.flatten().distinct().toList()

        val stationsAndLines = lines.map {
            it.stations.map { s -> "${it.name}:$s" }.toList()
        }.flatten().distinct().toList()

        val stationsToTestAgainst =
            stationsAndLines.filter { station ->
                stationsToTest.none {
                    Line.getStation(it) == Line.getStation(station)
                }
            }

        stationsToTest.forEach { toTest ->
            outer@ for (testAgainst in stationsToTestAgainst) {
                val fromStation = Line.getStation(toTest)
                val toStation = Line.getStation(testAgainst)

                if (haversineCalculator.distanceBetween(
                        start = stationFactory.get(fromStation).position,
                        end = stationFactory.get(toStation).position,
                    ) < 500.0
                ) {
                    val lineFrom = Line.getLine(toTest)
                    val lineTo = Line.getLine(testAgainst)

                    val linkTo = Pair(lineTo, toStation)
                    val linkFrom = Pair(lineFrom, fromStation)

                    links.getOrPut(lineFrom) { mutableSetOf() }.add(linkTo)
                    links.getOrPut(lineTo) { mutableSetOf() }.add(linkFrom)

                    virtualLinks.getOrPut(linkTo) { mutableSetOf() }.add(linkFrom)
                    virtualLinks.getOrPut(linkFrom) { mutableSetOf() }.add(linkTo)
                }
            }
        }
    }

    companion object {
        private val haversineCalculator = HaversineCalculator()

        private fun filterSharedLinedInterchanges(test: Line, testAgainst: Line): List<String> {
            val filteredInterchanges = mutableSetOf<String>()

            val interchanges = test.stations.filter { testAgainst.stations.contains(it) }
            if (interchanges.isEmpty() || interchanges.size <= 2) return interchanges
            val interchangeIndexes = interchanges.map { Pair(it, test.stations.indexOf(it)) }.sortedBy { it.second }
                .toMutableList() //note circle.  will grab first. its ok.
            var first = interchangeIndexes.removeFirst()
            val last = interchangeIndexes.removeLast()

            filteredInterchanges.addAll(listOf(first.first, last.first))

            interchangeIndexes.forEachIndexed { index, pair ->
                if (isNotContiguous(
                        previousIdx = first.second,
                        currentIdx = pair.second,
                        nextIdx = if (index < interchangeIndexes.size - 1) interchangeIndexes[index + 1].second else last.second
                    )
                )
                    filteredInterchanges.add(pair.first)

                first = pair
            }


            return filteredInterchanges.toList()
        }

        private fun isNotContiguous(previousIdx: Int, currentIdx: Int, nextIdx: Int): Boolean =
            (currentIdx > previousIdx + 1 || currentIdx < nextIdx - 1)

    }

}