package com.tabiiki.kotlinlab.factory

import com.tabiiki.kotlinlab.configuration.LinesConfig
import com.tabiiki.kotlinlab.configuration.TransportersConfig
import com.tabiiki.kotlinlab.model.Line

class LineFactory(
    transportConfig: TransportersConfig,
    linesConfig: LinesConfig
) {
    private val lines = linesConfig.lines.map { Line(it, transportConfig.trains) }
    fun get(id: String): Line = lines.find { it.id == id } ?: throw NoSuchElementException("Line missing")
    fun get(): List<String> = lines.map { it.id }
}