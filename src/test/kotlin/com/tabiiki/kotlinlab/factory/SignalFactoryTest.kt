package com.tabiiki.kotlinlab.factory

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
internal class SignalFactoryTest @Autowired constructor(
    private val signalFactory: SignalFactory,
    private val lineFactory: LineFactory
) {

    private val lines = lineFactory.get().map { lineFactory.get(it) }

    @Test
    fun `create section signals test`() {
        assertThat(signalFactory.get().filter { it.type == SignalType.SECTION }.size).isEqualTo(548)
    }

    @Test
    fun `create platform signals test`() {
        assertThat(signalFactory.get().filter { it.type == SignalType.PLATFORM }.size).isEqualTo(
            lines.map { it.stations.distinct() }.flatten().size * 2
        )
    }

}