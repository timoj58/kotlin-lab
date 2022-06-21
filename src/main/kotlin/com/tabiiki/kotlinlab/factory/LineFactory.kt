package com.tabiiki.kotlinlab.factory

import com.tabiiki.kotlinlab.configuration.LinesConfig
import com.tabiiki.kotlinlab.configuration.TransportersConfig
import com.tabiiki.kotlinlab.model.Line
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Repository
import javax.naming.ConfigurationException

@Repository
class LineFactory(
    @Value("\${network.time-step}") val timeStep: Long,
    transportConfig: TransportersConfig,
    linesConfig: LinesConfig
) {

    init {
        if (timeStep < 5) throw ConfigurationException("timestep is too small, minimum 10 ms")
    }

    private val lines =
        linesConfig.lines.map { Line(timeStep, it, transportConfig.get(), linesConfig.defaultLineCapacity!!) }

    fun get(id: String): Line = lines.find { it.id == id } ?: throw NoSuchElementException("Line missing")
    fun get(): List<String> = lines.map { it.id }
}