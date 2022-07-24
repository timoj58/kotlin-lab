package com.tabiiki.kotlinlab.integration

import com.tabiiki.kotlinlab.configuration.TransportersConfig
import com.tabiiki.kotlinlab.factory.SignalFactory
import com.tabiiki.kotlinlab.repo.JourneyRepo
import com.tabiiki.kotlinlab.repo.StationRepo
import com.tabiiki.kotlinlab.service.StationService
import com.tabiiki.kotlinlab.service.SwitchService
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles

//@Disabled
//@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
@ActiveProfiles("test-overground")
@SpringBootTest
class OvergroundLineControllerTest @Autowired constructor(
    @Value("\${network.start-delay}") val startDelay: Long,
    @Value("\${network.time-step}") val timeStep: Long,
    @Value("\${network.minimum-hold}") val minimumHold: Int,
    transportersConfig: TransportersConfig,
    stationService: StationService,
    stationRepo: StationRepo,
    signalFactory: SignalFactory,
    journeyRepo: JourneyRepo,
    switchService: SwitchService
) {
    val lineControllerTest = LineControllerTest(
        startDelay,
        timeStep,
        minimumHold,
        transportersConfig,
        stationService,
        stationRepo,
        signalFactory,
        journeyRepo,
        switchService
    )

    @ParameterizedTest
    @CsvSource(
        "gospel-oak",
        "highbury-islington",
        "london-euston",
        "romford",
        "london-liverpool-st",
        "stratford"
    )
    fun `test all transports complete a full journey on an overground line`(lineName: String) = runBlocking {
        lineControllerTest.test("overground", lineName, 3)
    }
}