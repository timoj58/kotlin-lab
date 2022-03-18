package com.tabiiki.kotlinlab.factory

import com.tabiiki.kotlinlab.configuration.LinesConfig
import com.tabiiki.kotlinlab.configuration.TransportConfig
import com.tabiiki.kotlinlab.configuration.TransportsConfig
import com.tabiiki.kotlinlab.model.Line

class LineFactory(
    transportConfig: TransportsConfig,
    linesConfig: LinesConfig
) {
    private val lines = linesConfig.lines.map { Line(it, transportConfig.trains) }
    fun get(id: String): Line? = lines.find {it.id == id}
}