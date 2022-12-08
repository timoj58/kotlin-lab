package com.tabiiki.kotlinlab.integration

import com.tabiiki.kotlinlab.configuration.TransportersConfig
import com.tabiiki.kotlinlab.factory.SignalFactory
import com.tabiiki.kotlinlab.factory.StationFactory
import com.tabiiki.kotlinlab.repo.JourneyRepo
import com.tabiiki.kotlinlab.repo.StationRepo
import com.tabiiki.kotlinlab.service.SwitchService
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles

@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
@ActiveProfiles("test-overground")
@SpringBootTest
class OvergroundLineControllerTest @Autowired constructor(
    @Value("\${network.start-delay}") val startDelay: Long,
    @Value("\${network.time-step}") val timeStep: Long,
    @Value("\${network.minimum-hold}") val minimumHold: Int,
    transportersConfig: TransportersConfig,
    stationRepo: StationRepo,
    signalFactory: SignalFactory,
    journeyRepo: JourneyRepo,
    switchService: SwitchService,
    stationFactory: StationFactory
) {
    val lineControllerTest = LineControllerTest(
        startDelay,
        timeStep,
        minimumHold,
        transportersConfig,
        stationRepo,
        signalFactory,
        journeyRepo,
        switchService,
        stationFactory
    )

    @ParameterizedTest
    @CsvSource(
        "gospel-oak",
        "highbury-islington",
        "london-euston",
        "romford",
        "london-liverpool-st",
        "stratford",
        //"elizabeth" TODO review this, likely around FATAL - already holding 47fb4cab-aaca-4c46-bf25-0a05b79c289c for (Elizabeth:POSITIVE, Elizabeth:418) next a4692531-0bab-44e0-a7ef-0f38299fa889
    )
    fun `test all transports complete a full journey on an overground line`(lineName: String) = runBlocking {
        lineControllerTest.test("overground", lineName, 15)
    }
}