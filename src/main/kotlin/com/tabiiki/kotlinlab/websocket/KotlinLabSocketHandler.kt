package com.tabiiki.kotlinlab.websocket

import org.springframework.stereotype.Component
import org.springframework.web.reactive.socket.WebSocketHandler
import org.springframework.web.reactive.socket.WebSocketSession
import reactor.core.publisher.Flux
import reactor.core.publisher.FluxSink
import reactor.core.publisher.Mono
import java.util.function.Consumer

@Component
class KotlinLabSocketHandler : WebSocketHandler {

    private lateinit var consumer: Consumer<String>
    private val receiver = Flux.create<String>({ sink: FluxSink<String?> ->
        consumer =
            Consumer<String> { t: String -> sink.next(t) }
    }, FluxSink.OverflowStrategy.BUFFER)

    init {
        receiver.subscribe()
    }

    override fun handle(session: WebSocketSession): Mono<Void> =
        session.send(receiver.map { session.textMessage(it) }).then()

    fun send(msg: String) {
        consumer.accept(msg)
    }
}
