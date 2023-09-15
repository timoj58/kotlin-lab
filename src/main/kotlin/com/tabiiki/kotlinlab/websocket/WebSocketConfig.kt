package com.tabiiki.kotlinlab.websocket

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.HandlerMapping
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping

@Configuration
class WebSocketConfig(
    private val kotlinLabSocketHandler: KotlinLabSocketHandler
) {

    @Bean
    fun handlerMapping(): HandlerMapping {
        val map = mapOf("/kotlin-lab" to kotlinLabSocketHandler)
        return SimpleUrlHandlerMapping(map)
    }
}
