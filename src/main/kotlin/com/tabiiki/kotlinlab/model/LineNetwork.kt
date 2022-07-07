package com.tabiiki.kotlinlab.model

data class NetworkNode(val station: String, val linked: MutableSet<String>)

data class LineNetwork(private val lines: List<Line>) {
    private val network: MutableList<NetworkNode> = mutableListOf()

    init {
        lines.map { listOf(it.stations.first(), it.stations.last()) }.flatten().distinct().forEach {
            network.add(
                NetworkNode(it, mutableSetOf("*"))
            )
        }

        lines.map { it.stations.subList(1, it.stations.lastIndex) }.flatten().distinct().forEach {
            if(!network.any { n -> n.station == it })
                network.add(NetworkNode(it, mutableSetOf()))
        }

        lines.map { it.stations }.forEach { lineStations ->
            lineStations.map { lineStations.subList(1, lineStations.lastIndex) }.flatten().forEach { station ->
                val idx = lineStations.indexOf(station)

                network.first { it.station == station }.linked.addAll(
                    setOf(lineStations[idx - 1], lineStations[idx + 1])
                )
            }
        }
    }

    fun getNodes(): List<NetworkNode> = network.toList()

}