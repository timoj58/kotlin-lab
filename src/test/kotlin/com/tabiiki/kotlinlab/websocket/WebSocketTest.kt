package com.tabiiki.kotlinlab.websocket

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient
import java.net.URI
import java.time.Duration

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
class WebSocketTest {

    @Test
    fun `connect to websocket`() {
        var receivedMessage = false
        val client = ReactorNettyWebSocketClient()

        client.execute(URI("ws://localhost:8080/kotlin-lab")) {
            it.receive()
                .doOnNext { receivedMessage = true }.then()
        }.subscribe()

        Thread.sleep(Duration.ofSeconds(10).toMillis())

        Assertions.assertThat(receivedMessage).isTrue
    }
}
