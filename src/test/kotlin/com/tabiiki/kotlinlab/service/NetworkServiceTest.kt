package com.tabiiki.kotlinlab.service

import com.tabiiki.kotlinlab.factory.LineFactory
import com.tabiiki.kotlinlab.factory.StationFactory
import org.junit.jupiter.api.Test
import org.mockito.Mockito

internal class NetworkServiceTest{

    val lineFactory = Mockito.mock(LineFactory::class.java)
    val stationFactory = Mockito.mock(StationFactory::class.java)

    val networkService: NetworkService = NetworkServiceImpl(lineFactory, stationFactory)

    @Test
    fun `network start test`(){
        //need to mock and confirm its launched....
        networkService.start()
    }

    @Test
    fun `network stop test`(){
        networkService.stop()
    }

}