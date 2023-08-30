package com.tabiiki.kotlinlab.configuration

import com.fasterxml.jackson.annotation.JsonAutoDetect
import com.fasterxml.jackson.annotation.JsonProperty
import com.tabiiki.kotlinlab.configuration.adapter.TransportersAdapter
import org.springframework.context.annotation.Configuration

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
data class TransportConfig(
    @JsonProperty("transport-id") val transportId: Int,
    @JsonProperty("capacity") val capacity: Int,
    @JsonProperty("weight") val weight: Int = 0,
    @JsonProperty("top-speed") val topSpeed: Int = 0,
    @JsonProperty("power") val power: Int = 0
)

@Configuration
class TransportersConfig(transportersAdapter: TransportersAdapter) {
    val trains = transportersAdapter.trains
    val ferries = transportersAdapter.ferries
    val cableCars = transportersAdapter.cableCars

    fun get() = trains + ferries + cableCars
}
