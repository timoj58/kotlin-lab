package com.tabiiki.kotlinlab.configuration

import com.fasterxml.jackson.annotation.JsonAutoDetect
import com.fasterxml.jackson.annotation.JsonProperty
import com.tabiiki.kotlinlab.configuration.adapter.TransportAdapter
import com.tabiiki.kotlinlab.model.Carrier
import org.springframework.context.annotation.Configuration

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
data class Transport(@JsonProperty("transport-id") val transportId: Int,
                    @JsonProperty("capacity") val capacity: Int)

@Configuration
class TransportConfig(transportAdapter: TransportAdapter) {
    val trains =  transportAdapter.trains.map { Carrier(it.transportId, it.capacity) }
}