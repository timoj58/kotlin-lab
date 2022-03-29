package com.tabiiki.kotlinlab.integration

import com.tabiiki.kotlinlab.service.NetworkService
import com.tabiiki.kotlinlab.service.StationMessage
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class NetworkServiceTest @Autowired constructor(
    val networkService: NetworkService
) {

    @Disabled
    @Test
    fun `run it`() = runBlocking()
    {
        val channel = Channel<StationMessage>()
        async { status(channel) }
        val res = async { networkService.start(channel) }

        //works well to write test before refactoring now...

    }

    private suspend fun status(channel: Channel<StationMessage>) {
        do {
            println(channel.receive()) //TODO apply tests
        }while (true)
    }

}