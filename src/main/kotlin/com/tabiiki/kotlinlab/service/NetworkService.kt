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

    override fun start() {
       // TODO("Not yet implemented")
    }

    override fun stop() {
       // TODO("Not yet implemented")
    }

}