package com.tabiiki.kotlinlab.service

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify

internal class NetworkControlServiceTest{

    val networkService  = mock(NetworkService::class.java)
    val networkControlService = NetworkControlServiceImpl(networkService)

    @Test
    fun `network controller init`(){
        verify(networkService).start()
    }
}