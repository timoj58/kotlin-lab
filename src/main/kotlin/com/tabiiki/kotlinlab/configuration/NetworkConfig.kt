package com.tabiiki.kotlinlab.configuration

import com.fasterxml.jackson.annotation.JsonAutoDetect
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.tabiiki.kotlinlab.configuration.adapter.LinesAdapter
import com.tabiiki.kotlinlab.configuration.adapter.TrainsAdapter
import com.tabiiki.kotlinlab.enumerator.LineType
import com.tabiiki.kotlinlab.model.Train
import org.springframework.context.annotation.Configuration
import java.io.File
import java.nio.file.Files

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
data class TrainConfig(@JsonProperty("id") val id: Int,
                      @JsonProperty("capacity") val capacity: Int)


@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
data class LineConfig(@JsonProperty("id") val id: String,
                      @JsonProperty("name") val name: String,
                      @JsonProperty("train") val train: Int,
                      @JsonProperty("total-trains") val totalTrains: Int,
                      @JsonProperty("stations") val stations: List<String>){
    var type: LineType? = null
}
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
data class LinesConfig(@JsonProperty("lines") val lines: List<LineConfig>)


@Configuration
class NetworkConfig(trainsAdapter: TrainsAdapter,
                    linesAdapter: LinesAdapter) {
    private val loadLines = mutableListOf<LineConfig>()
    val lines
        get() = loadLines.toList()
    val trains =  trainsAdapter.trains.map { Train(it.id, it.capacity) }

    init {
        val mapper = ObjectMapper(YAMLFactory())

        linesAdapter.lines.forEach { (type, configs) ->
            configs.forEach { config ->
                val loadedConfig = Files.newBufferedReader(File(config).toPath()).use {
                    mapper.readValue(it, LinesConfig::class.java)
                }

                loadedConfig.lines.forEach{
                    loadLines.add(it.apply { it.type = type })
                }
            }
        }
    }
}