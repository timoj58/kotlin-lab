package com.tabiiki.kotlinlab.model

import com.tabiiki.kotlinlab.util.LineBuilder
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class LineNetworkTest{

    @Test
    fun `test line network with simple route and one junction station`(){

        val lineNetwork = LineNetwork(listOf(LineBuilder().getLine(), LineBuilder().getLine3()))

        assertThat(lineNetwork.getNodes()).containsAll(
            listOf(
                NetworkNode("A", mutableSetOf("*")),
                NetworkNode("C", mutableSetOf("*")),
                NetworkNode("D", mutableSetOf("*")),
                NetworkNode("B", mutableSetOf("A","C","D")),
            )
        )

    }

    @Test
    fun `test line network with simple route on circle line`(){

        val lineNetwork = LineNetwork(listOf(LineBuilder().getCircleLine()))

        assertThat(lineNetwork.getNodes()).containsAll(
            listOf(
                NetworkNode("A", mutableSetOf("*")),
                NetworkNode("B", mutableSetOf("A","C")),
                NetworkNode("C", mutableSetOf("D","B")),
                NetworkNode("D", mutableSetOf("A","C")),
            )
        )

    }

    @Test
    fun `test line network with complex route on circle line`(){

        val lineNetwork = LineNetwork(listOf(LineBuilder().getCircleLine2()))

        assertThat(lineNetwork.getNodes()).containsAll(
            listOf(
                NetworkNode("E", mutableSetOf("*")),
                NetworkNode("A", mutableSetOf("*","B","C")),
                NetworkNode("B", mutableSetOf("E","A")),
                NetworkNode("C", mutableSetOf("A","D")),
                NetworkNode("D", mutableSetOf("A","C")),
            )
        )
    }

}