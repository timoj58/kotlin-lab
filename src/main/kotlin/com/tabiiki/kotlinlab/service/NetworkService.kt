package com.tabiiki.kotlinlab.service

import com.tabiiki.kotlinlab.factory.LineFactory
import com.tabiiki.kotlinlab.factory.StationFactory

interface NetworkService {
    fun start()
    fun stop()
}

class NetworkServiceImpl(
    lineFactory: LineFactory,
    stationFactory: StationFactory
) : NetworkService {

    private val lines = lineFactory.get().map { lineFactory.get(it) }
    private val stations = stationFactory.get().map { stationFactory.get(it) }
    private val lineControllers =
        lines.groupBy { line -> line.name }.values.map { byLine -> LineControllerServiceImpl(byLine, stations) }


    override fun start() {
        lineControllers.parallelStream().forEach { controller ->
            run {
                // controller.start()
                controller.regulate()
            }
        }
    }

    override fun stop() {
        // TODO("Not yet implemented")
    }

}