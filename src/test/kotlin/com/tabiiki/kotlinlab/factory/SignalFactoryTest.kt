package com.tabiiki.kotlinlab.factory

import kotlinx.coroutines.channels.Channel
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.*
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
    fun `create signals test`(){
        val channel = Channel<Signal>()
        signalFactory.create(lines, channel)

        assertThat(signalFactory.get().size).isEqualTo(548) //better way to test this in future.
    }

}