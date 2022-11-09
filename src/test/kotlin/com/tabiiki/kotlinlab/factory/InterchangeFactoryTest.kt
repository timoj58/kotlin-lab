package com.tabiiki.kotlinlab.factory


import com.tabiiki.kotlinlab.util.InterchangeFactoryBuilder
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test

class InterchangeFactoryTest {
    private val interchangeFactory = InterchangeFactoryBuilder().build()

    @Test
    fun `interchange init test `() {

        Assertions.assertThat(interchangeFactory.getLinks(key = "Jubilee", exclude = "").toList()).containsAll(
            listOf(
                Pair("DLR","95"),
                Pair("DLR","94"),
                Pair("Central","528"),
                Pair("District","615"),
                Pair("Circle","629")
            ),
        )

        Assertions.assertThat(interchangeFactory.getLinks(key = "Emirates Air Line", exclude = "").toList()).containsAll(
            listOf(
                Pair("Jubilee","396"),
            ),
        )

        Assertions.assertThat(interchangeFactory.getLinks(key = "RB1", exclude = "").toList()).containsAll(
            listOf(
                Pair("DLR","628"),
            ),
        )

    }


}