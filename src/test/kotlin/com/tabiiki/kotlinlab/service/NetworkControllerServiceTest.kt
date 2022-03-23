package com.tabiiki.kotlinlab.service

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify

internal class NetworkControllerServiceTest{

    val networkService  = mock(NetworkService::class.java)
    val networkControllerService = NetworkControllerServiceImpl(networkService)

    @Test
    fun `network controller init`(){
        verify(networkService).start()
    }
}