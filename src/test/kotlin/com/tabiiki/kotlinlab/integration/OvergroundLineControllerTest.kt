package com.tabiiki.kotlinlab.integration

import com.tabiiki.kotlinlab.configuration.TransportersConfig
import com.tabiiki.kotlinlab.factory.LineFactory
import com.tabiiki.kotlinlab.factory.SignalFactory
import com.tabiiki.kotlinlab.factory.StationFactory
import com.tabiiki.kotlinlab.repo.JourneyRepo
import com.tabiiki.kotlinlab.repo.StationRepo
import com.tabiiki.kotlinlab.service.SwitchService
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

@ActiveProfiles("test-overground")
@SpringBootTest
class OvergroundLineControllerTest @Autowired constructor(
    @Value("\${network.time-step}") val timeStep: Long,
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
        transportersConfig,
        stationRepo,
        signalFactory,
        journeyRepo,
        switchService,
        stationFactory,
        lineFactory
    )

    @Test
    fun `test elizabeth line`() = runBlocking {
        lineControllerTest.test("overground", "elizabeth", 5)
    }

    @Test
    fun `test gospel oak line`() = runBlocking {
        lineControllerTest.test("overground", "gospel-oak", 3)
    }

    @Test
    fun `test higbury-islington line`() = runBlocking {
        lineControllerTest.test("overground", "highbury-islington", 5)
    }

    @Test
    fun `test euston line`() = runBlocking {
        lineControllerTest.test("overground", "london-euston", 8)
    }

    @Test
    fun `test romford line`() = runBlocking {
        lineControllerTest.test("overground", "romford", 3)
    }

    @Test
    fun `test liverpool st line`() = runBlocking {
        lineControllerTest.test("overground", "london-liverpool-st", 5)
    }

    @Test
    fun `test stratford line`() = runBlocking {
        lineControllerTest.test("overground", "stratford", 6)
    }
}
