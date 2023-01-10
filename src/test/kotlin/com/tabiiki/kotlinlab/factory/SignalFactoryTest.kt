package com.tabiiki.kotlinlab.factory

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.util.UUID

@Disabled //fix this otherwise its pointless hard coding counts...
@SpringBootTest
internal class SignalFactoryTest @Autowired constructor(
    private val signalFactory: SignalFactory,
    private val lineFactory: LineFactory
) {

    // private val lines = lineFactory.get().map { lineFactory.get(it) }

    @Test
    fun `create section signals test`() {
        assertThat(signalFactory.get().filter { it.type == SignalType.SECTION }.size).isEqualTo(752)
    }

    @Test
    fun `create platform signals test`() {

        //need to fix this, and above.  for now hard coded.
        assertThat(signalFactory.get().filter { it.type == SignalType.PLATFORM }.size).isEqualTo(764)
    }

}