package com.tabiiki.kotlinlab.factory

import com.tabiiki.kotlinlab.configuration.NetworkConfig
import com.tabiiki.kotlinlab.model.Line

class LineFactory(
    networkConfig: NetworkConfig
) {
    private val lines = networkConfig.lines.map { Line(it, networkConfig.trains) }
    fun get(id: String): Line? = lines.find {it.id == id}
}