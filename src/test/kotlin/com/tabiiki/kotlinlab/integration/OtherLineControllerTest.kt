package com.tabiiki.kotlinlab.integration

import com.tabiiki.kotlinlab.configuration.TransportersConfig
import com.tabiiki.kotlinlab.factory.SignalFactory
import com.tabiiki.kotlinlab.factory.StationFactory
import com.tabiiki.kotlinlab.repo.StationRepo
import com.tabiiki.kotlinlab.service.SwitchService
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

@ActiveProfiles("test-other")
@SpringBootTest
class OtherLineControllerTest @Autowired constructor(
    @Value("\${network.time-step}") val timeStep: Long,
    transportersConfig: TransportersConfig,
    stationRepo: StationRepo,
    signalFactory: SignalFactory,
    switchService: SwitchService,
    stationFactory: StationFactory
) {
    val lineControllerTest = LineControllerTest(
        timeStep,
        transportersConfig,
        stationRepo,
        signalFactory,
        switchService,
        stationFactory
    )

    @Test
    fun `test cable`() =
        runBlocking {
            lineControllerTest.test("cable", "cable", 1)
        }

    @Test
    fun `test tram`() =
        runBlocking {
            lineControllerTest.test("tram", "tram", 6)
        }

    @Test
    fun `test dlr`() =
        runBlocking {
            lineControllerTest.test("dockland", "dlr", 4)
        }

    @Test
    fun `test river`() =
        runBlocking {
            lineControllerTest.test("river", "river", 4)
        }
}
