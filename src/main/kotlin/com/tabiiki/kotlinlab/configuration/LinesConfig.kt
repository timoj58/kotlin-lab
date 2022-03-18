package com.tabiiki.kotlinlab.configuration

import com.fasterxml.jackson.annotation.JsonAutoDetect
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.tabiiki.kotlinlab.configuration.adapter.LinesAdapter
import com.tabiiki.kotlinlab.enumerator.LineType
import org.springframework.context.annotation.Configuration
import java.io.File
import java.nio.file.Files

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
data class LineConfig(@JsonProperty("id") val id: String,
                      @JsonProperty("name") val name: String,
                      @JsonProperty("transport-id") val transportId: Int,
                      @JsonProperty("transport-capacity") val transportCapacity: Int,
                      @JsonProperty("stations") val stations: List<String>){
    var type: LineType? = null
}

@Configuration
class LinesConfig(linesAdapter: LinesAdapter) {

    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    companion object class Config(@JsonProperty("lines") val lines: List<LineConfig>)
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

                loaded.lines.forEach{
                    loadLines.add(it.apply { it.type = type })
                }
            }
        }
    }

}