package com.tabiiki.kotlinlab.integration

import com.tabiiki.kotlinlab.factory.LineFactory
import com.tabiiki.kotlinlab.service.NetworkService
import com.tabiiki.kotlinlab.service.StationMessage
import com.tabiiki.kotlinlab.util.IntegrationControl
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles

@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
@ActiveProfiles("test")
@SpringBootTest
class NetworkServiceTest @Autowired constructor(
    val networkService: NetworkService,
    private val lineFactory: LineFactory
) {
    val integrationControl = IntegrationControl()

    init {
        lineFactory.get().forEach { id -> with(integrationControl) { initControl(lineFactory.get(id)) } }
    }

    @Test
    fun `test all trains travel the line route`() = runBlocking()
    {
        val init = async { networkService.init() }
        delay(2000)
        val channel = Channel<StationMessage>()
        val res = async { networkService.start(channel) }
        val running =
            async { integrationControl.status(channel, listOf(init, res), 20) }
    }

}