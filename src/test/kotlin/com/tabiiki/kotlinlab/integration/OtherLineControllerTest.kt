package com.tabiiki.kotlinlab.integration

import com.tabiiki.kotlinlab.configuration.TransportersConfig
import com.tabiiki.kotlinlab.factory.SignalFactory
import com.tabiiki.kotlinlab.repo.JourneyRepo
import com.tabiiki.kotlinlab.repo.StationRepo
import com.tabiiki.kotlinlab.service.StationService
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
@ActiveProfiles("test-other")
@SpringBootTest
class OtherLineControllerTest @Autowired constructor(
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
        "tram,tram",
        "dockland,dlr",
        "river,rb1",
        "river,rb2",
        "river,rb4",
        "river,rb6",
        "cable,cable"
    )
    fun `test all transports complete a full journey on an other line`(lineType: String, lineName: String) =
        runBlocking {
            lineControllerTest.test(lineType, lineName, 3)
        }
}