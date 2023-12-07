package com.tabiiki.kotlinlab.controller

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.web.reactive.server.WebTestClient

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class StationControllerTest {

    @Autowired
    lateinit var webTestClient: WebTestClient

    @Test
    fun `get station information`() {
        webTestClient.get()
            .uri("/stations")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.size()").isEqualTo(477)
    }
}
