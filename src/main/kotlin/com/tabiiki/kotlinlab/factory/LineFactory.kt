package com.tabiiki.kotlinlab.factory

import com.tabiiki.kotlinlab.configuration.LinesConfig
import com.tabiiki.kotlinlab.configuration.TrainsConfig
import com.tabiiki.kotlinlab.model.Line

class LineFactory(
    trainsConfig: TrainsConfig,
    linesConfig: LinesConfig
) {
    private val lines = linesConfig.lines.map { Line(it, trainsConfig.trains) }
    fun get(id: String): Line? = lines.find {it.id == id}
}