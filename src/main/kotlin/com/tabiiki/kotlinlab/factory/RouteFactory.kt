package com.tabiiki.kotlinlab.factory

import com.tabiiki.kotlinlab.model.Line
import org.springframework.stereotype.Component

@Component
class RouteFactory(
    val lineFactory: LineFactory
) {
    private val lines: List<Line> = lineFactory.get().map { lineFactory.get(it) }
    private val interchanges = generateInterchanges(lines)

    fun getInterchanges(): List<String> = interchanges


    fun getAvailableRoutes(journey: Pair<String, String>): List<List<Pair<String, String>>> {
        val possibleRoutes = mutableListOf<List<Pair<String, String>>>()

        possibleRoutes.addAll(getDirectRoutes(journey))
        possibleRoutes.addAll(getInterchangeRoutes(journey))

        return possibleRoutes.distinct()
    }

    fun getDirectRoutes(journey: Pair<String, String>): List<List<Pair<String, String>>> =
        lines.filter { it.stations.contains(journey.first) && it.stations.contains(journey.second) }.map { line ->
            createRoute(line = line.name, stations = getSublist(journey.first, journey.second, line.stations))
        }.distinct()

    fun getInterchangeRoutes(journey: Pair<String, String>): List<List<Pair<String, String>>> {
        val possibleRoutes = mutableListOf<List<Pair<String, String>>>()

        val linesFrom = getLines(include = journey.first, exclude = journey.second)
        val linesTo = getLines(include = journey.second, exclude = journey.first)

        //performance is faster like this than with a flow based on some tests.
        linesFrom.forEach { lineFrom ->
            interchanges.filter { lineFrom.stations.contains(it) }.forEach { interchange ->
                traverseLines(
                    linesToTest = lines.filter { line -> line.stations.any { interchange == it } }.toMutableList(),
                    linesTo = linesTo,
                    route = addRoute(
                        route = mutableListOf(),
                        line = lineFrom,
                        from = journey.first,
                        to = interchange
                    ).toMutableList(),
                    from = interchange,
                    to = journey.second,
                    possibleRoutes = possibleRoutes
                )
            }
        }

        return possibleRoutes.distinct()
    }

    fun getLines(include: String, exclude: String): List<Line> =
        lines.filter { it.stations.contains(include) && it.stations.none { s -> s == exclude } }.filter {
            it.stations.any { station -> interchanges.contains(station) }
        }


    fun traverseLines(
        linesToTest: MutableList<Line>,
        linesTo: List<Line>,
        testedLines: MutableList<String>? = null,
        route: MutableList<Pair<String, String>>,
        from: String,
        to: String,
        possibleRoutes: MutableList<List<Pair<String, String>>>
    ) {
        if (linesToTest.isEmpty()) return
        do {
            val lineToTest = linesToTest.removeFirst()
            if (testedLines == null || testedLines.none { it == lineToTest.id }) {
                testedLines?.add(lineToTest.id)

                val lineInterchanges = interchanges.filter { lineToTest.stations.contains(it) }.toMutableList()

                if (linesTo.any { it.id == lineToTest.id })
                    possibleRoutes.add(addRoute(route = route, line = lineToTest, from = from, to = to))
                if (lineInterchanges.isEmpty())
                    route.removeLast()
                else
                    lineInterchanges.forEach { interchange ->
                        traverseLines(
                            linesToTest = filterLinesToTest(lineToTest, interchange, testedLines).toMutableList(),
                            linesTo = linesTo,
                            testedLines = testedLines ?: mutableListOf(),
                            route = addRoute(
                                route = route,
                                line = lineToTest,
                                from = from,
                                to = interchange
                            ).toMutableList(),
                            from = interchange,
                            to = to,
                            possibleRoutes = possibleRoutes
                        )
                    }
            }
        } while (linesToTest.isNotEmpty())
    }


    fun addRoute(route: List<Pair<String, String>>, line: Line, from: String, to: String): List<Pair<String, String>> =
        route.plus(createRoute(line = line.name, stations = getSublist(from, to, line.stations)))


    fun filterLinesToTest(lineToTest: Line, interchange: String, testedLines: List<String>?): List<Line> =
        lines.filter { line ->
            //using name as we do not want to interchange to the same line (diff route).  however, this maybe required for certain routes
            line.name != lineToTest.name && line.stations.any {
                interchange == it && testedLines?.none { t -> t == line.id } ?: true
            }
        }

    companion object {

        fun generateInterchanges(lines: List<Line>): List<String> {
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

        fun createRoute(line: String, stations: List<String>): List<Pair<String, String>> {
            val route = mutableListOf<Pair<String, String>>()
            for (idx in 0..stations.size - 2 step 1) {
                val from = stations[idx]
                val to = stations[idx + 1]
                route.add(Pair("$line:$from", "$line:$to"))
            }

            return route
        }

        //circle line will play havoc with this perhaps. TODO
        fun getSublist(from: String, to: String, stations: List<String>): List<String> {
            val fromIdx = stations.indexOf(from)
            val toIdx = stations.indexOf(to)

            return if (fromIdx < toIdx) stations.subList(fromIdx, toIdx + 1) else stations.subList(toIdx, fromIdx + 1)
                .reversed()
        }

    }
}