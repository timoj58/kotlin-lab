package com.tabiiki.kotlinlab.configuration

import com.fasterxml.jackson.annotation.JsonAutoDetect
import com.fasterxml.jackson.annotation.JsonProperty
import com.tabiiki.kotlinlab.configuration.adapter.TrainsAdapter
import com.tabiiki.kotlinlab.model.Train
import org.springframework.context.annotation.Configuration

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
data class Trains(@JsonProperty("id") val id: Int,
                  @JsonProperty("capacity") val capacity: Int)

@Configuration
class TrainsConfig(trainsAdapter: TrainsAdapter) {
    val trains =  trainsAdapter.trains.map { Train(it.id, it.capacity) }
}