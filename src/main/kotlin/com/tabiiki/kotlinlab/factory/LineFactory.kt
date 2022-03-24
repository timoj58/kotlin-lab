package com.tabiiki.kotlinlab.factory

import com.tabiiki.kotlinlab.configuration.LinesConfig
import com.tabiiki.kotlinlab.configuration.TransportersConfig
import com.tabiiki.kotlinlab.model.Line
import org.springframework.stereotype.Repository

@Repository
class LineFactory(
    transportConfig: TransportersConfig,
    linesConfig: LinesConfig
) {
    private val lines = linesConfig.lines.map { Line(it, transportConfig.get()) }
    fun get(id: String): Line = lines.find { it.id == id } ?: throw NoSuchElementException("Line missing")
    fun get(): List<String> = lines.map { it.id }
}