package com.tabiiki.kotlinlab.model

import com.tabiiki.kotlinlab.configuration.TransportConfig

data class Transport(val config: TransportConfig){
    val transportId = config.transportId
    val capacity = config.capacity
    val currentStation: String? = null
}