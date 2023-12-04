package com.tabiiki.kotlinlab.integration

import com.tabiiki.kotlinlab.configuration.TransportersConfig
import com.tabiiki.kotlinlab.factory.LineFactory
import com.tabiiki.kotlinlab.factory.SignalFactory
import com.tabiiki.kotlinlab.factory.StationFactory
import com.tabiiki.kotlinlab.repo.JourneyRepo
import com.tabiiki.kotlinlab.repo.StationRepo
import com.tabiiki.kotlinlab.service.SwitchService
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

@ActiveProfiles("test-underground")
@SpringBootTest
class UndergroundLineControllerTest @Autowired constructor(
    @Value("\${network.time-step}") val timeStep: Long,
    @Value("\${network.minimum-hold}") val minimumHold: Int,
    transportersConfig: TransportersConfig,
    stationRepo: StationRepo,
    signalFactory: SignalFactory,
    journeyRepo: JourneyRepo,
    switchService: SwitchService,
    stationFactory: StationFactory,
    lineFactory: LineFactory
) {
    val lineControllerTest = LineControllerTest(
        timeStep,
        minimumHold,
        transportersConfig,
        stationRepo,
        signalFactory,
        journeyRepo,
        switchService,
        stationFactory,
        lineFactory
    )

    @Test
    fun `test city`() = runBlocking {
        lineControllerTest.test("underground", "city", 1)
    }

    @Test
    fun `test jubilee`() = runBlocking {
        lineControllerTest.test("underground", "jubilee", 4)
    }

    @Test
    fun `test district`() = runBlocking {
        lineControllerTest.test("underground", "district", 4)
    }

    @Test
    fun `test northern`() = runBlocking {
        lineControllerTest.test("underground", "northern", 4)
    }

    @Test
    fun `test piccadilly`() = runBlocking {
        lineControllerTest.test("underground", "piccadilly", 5)
    }

    @Test
    fun `test metropolitan`() = runBlocking {
        lineControllerTest.test("underground", "metropolitan", 5)
    }

    @Test
    fun `test bakerloo`() = runBlocking {
        lineControllerTest.test("underground", "bakerloo", 4)
    }

    @Test
    fun `test victoria`() = runBlocking {
        lineControllerTest.test("underground", "victoria", 2)
    }

    @Test
    fun `test hammersmith`() = runBlocking {
        lineControllerTest.test("underground", "hammersmith", 10)
    }

    @Test
    fun `test circle`() = runBlocking {
        lineControllerTest.test("underground", "circle", 8)
    }

    @Test
    fun `test central`() = runBlocking {
        lineControllerTest.test("underground", "central", 8)
    }
}
