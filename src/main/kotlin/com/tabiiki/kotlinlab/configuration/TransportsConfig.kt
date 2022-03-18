package com.tabiiki.kotlinlab.configuration

import com.fasterxml.jackson.annotation.JsonAutoDetect
import com.fasterxml.jackson.annotation.JsonProperty
import com.tabiiki.kotlinlab.configuration.adapter.TransportAdapter
import org.springframework.context.annotation.Configuration

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
data class TransportConfig(@JsonProperty("transport-id") val transportId: Int,
                           @JsonProperty("capacity") val capacity: Int)

@Configuration
class TransportsConfig(transportAdapter: TransportAdapter) {
    val trains =  transportAdapter.trains
    val ferries = transportAdapter.ferries
    val cableCars = transportAdapter.cableCars
}