package com.tabiiki.kotlinlab.factory

import com.tabiiki.kotlinlab.configuration.LinesConfig
import com.tabiiki.kotlinlab.configuration.TransportersConfig
import com.tabiiki.kotlinlab.model.Line
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Repository

@Repository
class LineFactory(
    @Value("\${network.time-step}") timeStep: Long,
    transportConfig: TransportersConfig,
    linesConfig: LinesConfig
) {
    private val lines = linesConfig.lines.map { Line(timeStep, it, transportConfig.get()) }
    fun get(id: String): Line = lines.find { it.id == id } ?: throw NoSuchElementException("Line missing")
    fun get(): List<String> = lines.map { it.id }
}