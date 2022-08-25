package com.tabiiki.kotlinlab.model

import java.util.concurrent.atomic.AtomicInteger

interface ICarriage {
    suspend fun embark()
    suspend fun disembark()
}

data class Carriage(
    val capacity: Int
): ICarriage {

    private val currentLoad: AtomicInteger = AtomicInteger(0)

    override suspend fun embark() {
        //needs a channel, to allow people to get on and off...only running when transporter stopped..
        TODO("Not yet implemented")
    }

    override suspend fun disembark() {
        TODO("Not yet implemented")
    }
}