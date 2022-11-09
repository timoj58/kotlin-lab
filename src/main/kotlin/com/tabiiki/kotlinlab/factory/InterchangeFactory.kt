package com.tabiiki.kotlinlab.factory

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
        val interchangesWithLineTypes = generateInterchanges(lines)
        generateVirtualInterchanges(lines, interchangesWithLineTypes)
    }

    fun getLinks(key: String, exclude: String) = links[key]!!.filter { it.second != exclude }

    fun isVirtualLink(link: Pair<String, String>) = virtualLinks.containsKey(link)

    fun getVirtualLinkTo(link: Pair<String, String>, line: String) = virtualLinks[link]?.firstOrNull { it.first == line }

    fun getLineIdsByLink(link: Pair<String, String>) =
        lines.filter { it.name == link.first }.filter { it.stations.contains(link.second) }.map { it.id }
    fun getLines(include: String, exclude: String): List<Line> =
        lines.filter { it.stations.contains(include) && it.stations.none { s -> s == exclude } }

    private fun generateInterchanges(lines: List<Line>): List<String> {
        val result = mutableListOf<String>()
        lines.forEach { line ->
            links[line.name] = mutableSetOf()

            val otherLines = lines.filter { it.id != line.id }
            line.stations.forEach { station ->
                otherLines.forEach { other ->
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
                    it.substringAfter(":") == station.substringAfter(
                        ":"
                    )
                }
            }

        stationsToTest.forEach { toTest ->
            outer@ for (testAgainst in stationsToTestAgainst) {
                val fromStation = toTest.substringAfter(":")
                val toStation = testAgainst.substringAfter(":")

                if (haversineCalculator.distanceBetween(
                        start = stationFactory.get(fromStation).position,
                        end = stationFactory.get(toStation).position,
                    ) < 500.0
                ) {
                    val lineFrom = toTest.substringBefore(":")
                    val lineTo = testAgainst.substringBefore(":")

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
    }

}