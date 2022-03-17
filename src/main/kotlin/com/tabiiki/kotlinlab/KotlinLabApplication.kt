package com.tabiiki.kotlinlab

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.web.reactive.config.EnableWebFlux

@SpringBootApplication
@EnableWebFlux
class KotlinLabApplication

fun main(args: Array<String>) {
	runApplication<KotlinLabApplication>(*args)
}
