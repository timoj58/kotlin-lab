package com.tabiiki.kotlinlab.factory

import com.tabiiki.kotlinlab.model.Line
import com.tabiiki.kotlinlab.service.RouteEnquiry
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

data class AvailableRoute(val route: MutableList<Pair<String, String>>)

private data class RouteNode(
    val key: Pair<String, String>,
    val children: MutableSet<RouteNode>,
    val parent: MutableList<Pair<String, String>> = mutableListOf()
)

@Component
class RouteFactory(
    private val interchangeFactory: InterchangeFactory
) {
    private val memoized: ConcurrentHashMap<Pair<String, String>, MutableList<AvailableRoute>> =
        ConcurrentHashMap()

    fun isSelectableStation(station: String) = interchangeFactory.stations.any { it == station }

    suspend fun generateAvailableRoutes(enquiry: RouteEnquiry) {
        if (memoized.contains(enquiry.route) && memoized[enquiry.route]!!.isNotEmpty())
            memoized[enquiry.route]!!.forEach { enquiry.channel.send(it) }
        else {
            memoized[enquiry.route] = mutableListOf()
            getDirectRoutes(enquiry)
            getInterchangeRoutes(enquiry)
        }
    }

    private suspend fun getDirectRoutes(enquiry: RouteEnquiry) {
        val journey = enquiry.route
        interchangeFactory.lines.filter { it.stations.contains(journey.first) && it.stations.contains(journey.second) }
            .map { line -> createRoute(line = line.name, from = journey.first, to = journey.second) }
            .distinct()
            .map { AvailableRoute(it.toMutableList()) }.forEach {
                it.let {
                    memoized[enquiry.route]!!.add(it)
                    enquiry.channel.send(it)
                }
            }
    }

    private suspend fun getInterchangeRoutes(enquiry: RouteEnquiry) {
        val journey = enquiry.route

        val linesFrom = interchangeFactory.getLines(include = journey.first, exclude = journey.second)
        val linesTo = interchangeFactory.getLines(include = journey.second, exclude = journey.first).map { it.id }

        linesFrom.forEach { lineFrom ->
            val links = interchangeFactory.getLinks(key = lineFrom.name, exclude = journey.first)
            val root = Pair(lineFrom.name, journey.first)
            val route = RouteNode(
                key = root,
                children = links.map {
                    RouteNode(
                        key = it,
                        children = mutableSetOf(),
                        parent = mutableListOf(root)
                    )
                }.toMutableSet()
            )

            processLinks(
                route = route,
                links = route.children.toMutableList(),
                linesTo = linesTo,
                enquiry = enquiry,
            )
        }
        enquiry.channel.close()
    }

    private suspend fun processLinks(
        route: RouteNode,
        links: MutableList<RouteNode>,
        linesTo: List<String>,
        enquiry: RouteEnquiry,
    ) {
        if (links.isEmpty() || links.first().parent.size > enquiry.depth) return

        do {
            val to = enquiry.route.second
            val link = links.removeFirst()
            if (linesTo.any { interchangeFactory.getLineIdsByLink(link.key).contains(it) })
                createAvailableRoute(node = link, to = to).let {
                    enquiry.channel.send(it)
                    memoized[enquiry.route]!!.add(it) }
            else
                interchangeFactory.getLinks(key = link.key.first, exclude = link.key.second)
                    .filter { filterOutRepeats(link, it.second) }
                    .toMutableList().let {
                        link.children.addAll(
                            it.map { next ->
                                RouteNode(
                                    key = next,
                                    children = mutableSetOf(),
                                    parent = link.parent.plus(link.key).toMutableList(),
                                )
                            }.toMutableList()
                        )
                        processLinks(
                            route = route,
                            links = link.children
                                .sortedByDescending { child -> linesTo.contains(child.key.first) }.toMutableList(),
                            linesTo = linesTo,
                            enquiry = enquiry,
                        )
                    }
        } while (links.isNotEmpty())
    }

    private fun createAvailableRoute(node: RouteNode, to: String): AvailableRoute {
        val route = mutableListOf<Pair<String, String>>()
        node.parent.add(node.key)
        for (idx in 0 until node.parent.size - 1 step 1) {
            val parentFrom = node.parent[idx]
            val parentTo = node.parent[idx + 1]
            route.addAll(
                createRouteFromLine(
                    from = parentFrom,
                    to = parentTo,
                    line = parentFrom.first,
                )
            )
        }

        route.addAll(
            createRouteFromLine(
                from = node.key,
                to = Pair(node.key.first, to),
                line = node.key.first,
            )
        )

        return AvailableRoute(route = route)
    }

    private fun createRouteFromLine(
        from: Pair<String, String>,
        to: Pair<String, String>,
        line: String
    ): List<Pair<String, String>> {
        val isVirtualFrom = interchangeFactory.isVirtualLink(link = from)
        val isVirtualTo = interchangeFactory.isVirtualLink(link = to)

        if ((isVirtualFrom || isVirtualTo) && from.first != to.first){
            val virtualTo = interchangeFactory.getVirtualLinkTo(to, from.first)
            if(virtualTo != null) return createVirtualRoute(from = from, to = virtualTo)
        }

        val lineDetails = interchangeFactory.lines.firstOrNull {
            it.name == line && it.stations.containsAll(listOf(from.second, to.second))
        }

        if (lineDetails == null) {
            val linesFrom = interchangeFactory.lines.filter { it.name == line && it.stations.contains(from.second) }
            val linesTo = interchangeFactory.lines.filter { it.name == line && it.stations.contains(to.second) }

            for (interchange in interchangeFactory.getLinks(key = line, exclude = "").map { it.second }) {
                val lineFrom = linesFrom.firstOrNull { it.stations.contains(interchange) }
                val lineTo = linesTo.firstOrNull { it.stations.contains(interchange) }

                if (lineFrom != null && lineTo != null)
                    return createRoute(line = lineFrom.name, from = from.second, to = interchange).toMutableList()
                        .plus(createRoute(line = lineTo.name, from = interchange, to = to.second))

            }
            throw RuntimeException("no route found for $from $isVirtualFrom $to $isVirtualTo and $line")
        } else return createRoute(line = lineDetails.name, from = from.second, to = to.second)
    }


    companion object {
        private fun createRoute(line: String, from: String, to: String): List<Pair<String, String>> =
            listOf(Pair("$line:$from", "$line:$to"))

        private fun createVirtualRoute(from: Pair<String, String>, to: Pair<String, String>): List<Pair<String, String>> =
            listOf(Pair("${from.first}:${from.second}", "${to.first}:${to.second}"))

        private fun filterOutRepeats(link: RouteNode, station: String): Boolean =
            link.parent.map { it.second }.none { it == station }
    }
}