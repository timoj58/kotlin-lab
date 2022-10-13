package com.tabiiki.kotlinlab.factory

import com.tabiiki.kotlinlab.model.Line
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

@Component
class RouteFactory(
    val lineFactory: LineFactory
) {
    private val lines: List<Line> = lineFactory.get().map { lineFactory.get(it) }
    private val interchanges = generateInterchanges(lines)
    private val memoized: ConcurrentHashMap<Pair<String, String>, List<List<Pair<String, String>>>> =
        ConcurrentHashMap()

    fun getInterchanges(): List<String> = interchanges

    fun getAvailableRoutes(journey: Pair<String, String>): List<List<Pair<String, String>>> =
        memoized.getOrElse(journey) {
            memoized[journey] = getDirectRoutes(journey).toMutableList().plus(getInterchangeRoutes(journey)).distinct()
            return memoized[journey]!!
        }

    private fun getDirectRoutes(journey: Pair<String, String>): List<List<Pair<String, String>>> =
        lines.filter { it.stations.contains(journey.first) && it.stations.contains(journey.second) }.map { line ->
            createRoute(line = line.name, stations = getSublist(journey.first, journey.second, line.stations))
        }.distinct()

    private fun getInterchangeRoutes(journey: Pair<String, String>): List<List<Pair<String, String>>> {
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

    private fun getLines(include: String, exclude: String): List<Line> =
        lines.filter { it.stations.contains(include) && it.stations.none { s -> s == exclude } }.filter {
            it.stations.any { station -> interchanges.contains(station) }
        }


    private fun traverseLines(
        linesToTest: MutableList<Line>,
        linesTo: List<Line>,
        testedLines: MutableList<String>? = null,
        route: MutableList<Pair<String, String>>,
        from: String,
        to: String,
        possibleRoutes: MutableList<List<Pair<String, String>>>,
        testForRepeatInterchange: Boolean = false
    ) {
        if (linesToTest.isEmpty()) return
        do {
            val lineToTest = linesToTest.removeFirst()
            if (testForRepeatInterchange
                &&
                possibleRoutes.flatten().any { pair ->
                    pair.first == "${lineToTest.name}:$to" || pair.second == "${lineToTest.name}:$to"
                }
            ) return

            if (testedLines == null || testedLines.none { it == lineToTest.id }) {
                testedLines?.add(lineToTest.id)

                val lineInterchanges = interchanges.filter { lineToTest.stations.contains(it) }.toMutableList()

                if (linesTo.any { it.id == lineToTest.id })
                    possibleRoutes.add(addRoute(route = route, line = lineToTest, from = from, to = to))
                if (lineInterchanges.isEmpty())
                    route.removeLast()
                else
                //need to filter out circle lines using same station as interchange
                    lineInterchanges.filter { it != from }.forEach { interchange ->
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
                            possibleRoutes = possibleRoutes,
                            testForRepeatInterchange = true
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


    private fun filterLinesToTest(lineToTest: Line, interchange: String, testedLines: List<String>?): List<Line> =
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

        fun getSublist(from: String, to: String, stations: List<String>): List<String> {
            val fromIdx = stations.indexOf(from)
            val toIdx = stations.indexOf(to)
            val fromCount = stations.count { it == from }
            val toCount = stations.count { it == to }

            return when (fromCount + toCount) {
                2 -> if (fromIdx < toIdx) stations.subList(fromIdx, toIdx + 1) else stations.subList(toIdx, fromIdx + 1)
                    .reversed()
                //circle line  418 to 418 .. return the shortest route
                3 -> getLeastStops(from, to, stations)
                else -> throw RuntimeException("invalid station $from $to count on route $fromCount + $toCount")
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

            if (idx2 > idx1a) possibleRoutes.add(stations.subList(idx1a, idx2))
            if (idx2 > idx1b) possibleRoutes.add(stations.subList(idx1b, idx2))
            if (idx2 < idx1a) possibleRoutes.add(stations.subList(idx2, idx1a).reversed())
            if (idx2 < idx1b) possibleRoutes.add(stations.subList(idx2, idx1b).reversed())

            return possibleRoutes.minBy { it.size }
        }

    }
}