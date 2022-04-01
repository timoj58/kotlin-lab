package com.tabiiki.kotlinlab.service

import com.tabiiki.kotlinlab.model.Transport
import com.tabiiki.kotlinlab.util.LineBuilder
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class LineSectionServiceTest {


    private val lineSectionService = LineSectionServiceImpl()

    @Test
    fun `get the train to release next`() {

        val transport = Transport(config = LineBuilder().transportConfig, lineId = "1", timeStep = 1).also {
            it.linePosition = Pair("A", "B")
        }
        val transport2 = Transport(config = LineBuilder().transportConfig, lineId = "1", timeStep = 1).also {
            it.linePosition = Pair("A", "B")
        }


        lineSectionService.enterSection(transport, listOf())
        lineSectionService.enterSection(transport2, listOf())

        assertThat(lineSectionService.getNext(Pair("A", "B"))!!.first.id).isEqualTo(transport.id)
        assertThat(lineSectionService.getNext(Pair("A", "B"))!!.first.id).isEqualTo(transport2.id)
        assertThat(lineSectionService.getNext(Pair("A", "B"))).isNull()


    }

}