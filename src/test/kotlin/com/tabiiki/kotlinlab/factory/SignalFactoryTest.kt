package com.tabiiki.kotlinlab.factory

import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
internal class SignalFactoryTest @Autowired constructor(
    private val signalFactory: SignalFactory,
    private val lineFactory: LineFactory
){

    private val lines = lineFactory.get().map { lineFactory.get(it) }


    @Test
    fun `create signals test`() = runBlocking{
        val channel = Channel<Signal>()
        val job = async {  signalFactory.create(lines, channel) }

        delay(100)

        assertThat(signalFactory.get().size).isEqualTo(548)
        job.cancelAndJoin()
    }

}