package com.tabiiki.kotlinlab.integration

import com.tabiiki.kotlinlab.service.NetworkService
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class NetworkServiceTest @Autowired constructor(
    val networkService: NetworkService
) {

    //@Test
    fun `run it`() = runBlocking()
    {
        val res = async {  networkService.start()}
        //should test the whole network is launched
        //currently using this to run the full app...
    }

}