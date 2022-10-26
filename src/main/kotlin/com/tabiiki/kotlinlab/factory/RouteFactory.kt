package com.tabiiki.kotlinlab.factory

import com.tabiiki.kotlinlab.model.Line
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

data class AvailableRoutes(val routes: List<List<Pair<String, String>>>)

@Component
class RouteFactory(
    private val interchangeFactory: InterchangeFactory
) {
    private val memoized: ConcurrentHashMap<Pair<String, String>, AvailableRoutes> =
        ConcurrentHashMap()

    fun isSelectableStation(station: String) = interchangeFactory.stations.any { it == station }

    fun getAvailableRoutes(journey: Pair<String, String>): AvailableRoutes =
        memoized.getOrElse(journey) {
            memoized[journey] = AvailableRoutes(getDirectRoutes(journey).toMutableList().plus(getInterchangeRoutes(journey)))
            return memoized[journey]!!
        }

    private fun getDirectRoutes(journey: Pair<String, String>): List<List<Pair<String, String>>> =
        interchangeFactory.lines.filter { it.stations.contains(journey.first) && it.stations.contains(journey.second) }.map { line ->
            createRoute(line = line.name, stations = getSublist(journey.first, journey.second, line.stations))
        }.distinct()

    private fun getInterchangeRoutes(journey: Pair<String, String>): List<List<Pair<String, String>>> {
        val possibleRoutes = mutableListOf<List<Pair<String, String>>>()

        val linesFrom = interchangeFactory.getLines(include = journey.first, exclude = journey.second)
        val linesTo = interchangeFactory.getLines(include = journey.second, exclude = journey.first)

        //performance is faster like this than with a flow based on some tests.
        linesFrom.forEach { lineFrom ->
            interchangeFactory.interchanges.filter { lineFrom.stations.contains(it) }.forEach { interchange ->
                traverseLines(
                    linesToTest = interchangeFactory.linesToTest(interchange),
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


    private fun traverseLines(
        linesToTest: MutableList<Line>,
        linesTo: List<Line>,
        testedLines: MutableList<String>? = null,
        route: MutableList<Pair<String, String>>,
        from: String,
        to: String,
        possibleRoutes: MutableList<List<Pair<String, String>>>,
    ) {
        if (linesToTest.isEmpty()) return
        do {
            val lineToTest = linesToTest.removeFirst()

            if (testedLines == null || testedLines.none { it == lineToTest.id }) {
                testedLines?.add(lineToTest.id)

                val lineInterchanges = interchangeFactory.interchanges.filter { lineToTest.stations.contains(it) }.toMutableList()

                //this ensures we do not add routes that bypass the destination by using it as an interchange
                if (linesTo.any { it.id == lineToTest.id } && route.none { it.second.substringAfter(":") == to })
                    possibleRoutes.add(addRoute(route = route, line = lineToTest, from = from, to = to))

                if (lineInterchanges.isEmpty())
                    route.removeLast()
                else
                    lineInterchanges.forEach { interchange ->
                        traverseLines(
                            linesToTest = interchangeFactory.filterLinesToTest(lineToTest, interchange, testedLines).toMutableList(),
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
                            possibleRoutes = possibleRoutes,
                        )
                    }
            }
        } while (linesToTest.isNotEmpty())
    }


    private fun addRoute(
        route: List<Pair<String, String>>,
        line: Line,
        from: String,
        to: String
    ): List<Pair<String, String>> =
        route.plus(createRoute(line = line.name, stations = getSublist(from, to, line.stations)))


    companion object {

        fun createRoute(line: String, stations: List<String>): List<Pair<String, String>> {
            val route = mutableListOf<Pair<String, String>>()
            for (idx in 0..stations.size - 2 step 1) {
                val from = stations[idx]
                val to = stations[idx + 1]
                route.add(Pair("$line:$from", "$line:$to"))
            }

            return route
        }

        fun getSublist(from: String, to: String, stations: List<String>): List<String> {
            val fromIdx = stations.indexOf(from)
            val toIdx = stations.indexOf(to)
            val fromCount = stations.count { it == from }
            val toCount = stations.count { it == to }

            return when (fromCount + toCount) {
                2 -> if (fromIdx < toIdx) stations.subList(fromIdx, toIdx + 1) else stations.subList(toIdx, fromIdx + 1)
                    .reversed()
                //circle line  418 to 418 .. return the shortest route
                3, 4 -> getLeastStops(from, to, stations)
                else -> throw RuntimeException("invalid station $from $to count on route $fromCount + $toCount $stations")
            }
        }

        private fun getLeastStops(from: String, to: String, stations: List<String>): List<String> {
            //for each index of either, find the shortest distance...fixed on basis only one line has two stations.  circle.
            val fromFirstIdx = stations.indexOfFirst { it == from }
            val fromLastIdx = stations.indexOfLast { it == from }
            val toFirstIdx = stations.indexOfFirst { it == to }
            val toLastIdx = stations.indexOfLast { it == to }

            return if (toFirstIdx == toLastIdx)
                calcShortestRoute(fromFirstIdx, fromLastIdx, toFirstIdx, stations) else
                calcShortestRoute(toFirstIdx, toLastIdx, fromFirstIdx, stations).reversed()
        }

        private fun calcShortestRoute(idx1a: Int, idx1b: Int, idx2: Int, stations: List<String>): List<String> {
            val possibleRoutes = mutableListOf<List<String>>()

            if (idx2 > idx1a) possibleRoutes.add(stations.subList(idx1a, idx2 + 1))
            if (idx2 > idx1b) possibleRoutes.add(stations.subList(idx1b, idx2 + 1))
            if (idx2 < idx1a) possibleRoutes.add(stations.subList(idx2, idx1a + 1).reversed())
            if (idx2 < idx1b) possibleRoutes.add(stations.subList(idx2, idx1b + 1).reversed())

            return possibleRoutes.minBy { it.size }
        }

    }
}