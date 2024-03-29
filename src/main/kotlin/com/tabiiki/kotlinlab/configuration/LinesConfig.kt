package com.tabiiki.kotlinlab.configuration

import com.fasterxml.jackson.annotation.JsonAutoDetect
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.tabiiki.kotlinlab.configuration.adapter.LinesAdapter
import org.springframework.context.annotation.Configuration
import java.io.File
import java.nio.file.Files

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
data class LineConfig(
    @JsonProperty("id") val id: String,
    @JsonProperty("name") val name: String,
    @JsonProperty("transport-id") val transportId: Int,
    @JsonProperty("line-capacity") val lineCapacity: Int,
    @JsonProperty("stations") val stations: List<String>,
    @JsonProperty("depots") val depots: List<String> = listOf(),
    @JsonProperty("switch-track-distance") val switchTrackDistance: Double? = null,
    @JsonProperty("override-is-clear") val overrideIsClear: Boolean? = null
) {
    var type: LineType? = null
}

enum class LineType {
    UNDERGROUND, OVERGROUND, RIVER, CABLE, DOCKLAND, TRAM;

    fun notThis(): List<LineType> = LineType.values().filter { it != this }
}

@Configuration
class LinesConfig(linesAdapter: LinesAdapter) {

    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    companion object
    class Config(@JsonProperty("lines") val lines: List<LineConfig>)

    private val loadLines = mutableListOf<LineConfig>()
    val lines
        get() = loadLines.toList()

    init {
        val mapper = ObjectMapper(YAMLFactory())

        linesAdapter.lines.forEach { (type, configs) ->
            configs.forEach { config ->
                val loaded = Files.newBufferedReader(File(config).toPath()).use {
                    mapper.readValue(it, Config::class.java)
                }

                loadLines.addAll(loaded.lines.map { it.apply { it.type = type } })
            }
        }
    }
}
