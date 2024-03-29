package com.tabiiki.kotlinlab.integration

import com.tabiiki.kotlinlab.factory.LineFactory
import com.tabiiki.kotlinlab.model.TransportMessage
import com.tabiiki.kotlinlab.service.NetworkService
import com.tabiiki.kotlinlab.service.StationMessage
import com.tabiiki.kotlinlab.util.IntegrationControl
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

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
    fun `test all trains travel the line route`(): Unit = runBlocking {
        val init = launch { networkService.init() }
        delay(2000)
        val channel = Channel<StationMessage>()
        val tracker = Channel<TransportMessage>()
        val res = launch { networkService.start(stationReceiver = channel, transportReceiver = tracker) }
        async { integrationControl.status(channel, listOf(init, res), 5) {} }
    }
}
