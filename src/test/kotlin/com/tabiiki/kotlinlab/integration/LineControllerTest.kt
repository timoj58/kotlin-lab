package com.tabiiki.kotlinlab.integration

import com.tabiiki.kotlinlab.configuration.LinesConfig
import com.tabiiki.kotlinlab.configuration.TransportersConfig
import com.tabiiki.kotlinlab.configuration.adapter.LinesAdapter
import com.tabiiki.kotlinlab.factory.LineFactory
import com.tabiiki.kotlinlab.model.Transport
import com.tabiiki.kotlinlab.service.LineConductor
import com.tabiiki.kotlinlab.service.LineControllerImpl
import com.tabiiki.kotlinlab.service.StationMessage
import com.tabiiki.kotlinlab.service.StationService
import com.tabiiki.kotlinlab.util.IntegrationControl
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import java.time.LocalDateTime

@ActiveProfiles("test")
@SpringBootTest
class LineControllerTest @Autowired constructor(
    @Value("\${network.start-delay}") val startDelay: Long,
    @Value("\${network.time-step}") val timeStep: Long,
    val transportersConfig: TransportersConfig,
    val platformConductor: LineConductor,
    val stationService: StationService
) {

    val integrationControl = IntegrationControl()

    //NOTE: broken up as seem to block eventually due to laptop / process etc.  To confirm

    @ParameterizedTest
    @CsvSource(
        "city",
        "metropolitan",
        "central",
        "northern",
        "district",
    )
    fun `test all transports complete a full journey on an underground line`(lineName: String) {
        theTest(lineName)
    }

    @ParameterizedTest
    @CsvSource(
        "victoria",
        "circle",
        "jubilee",
        "bakerloo",
        "hammersmith",
    )
    fun `test all transports complete a full journey on an underground line (pt II)`(lineName: String) {
        theTest(lineName)
    }

    private fun theTest(lineName: String) = runBlocking {

        println(LocalDateTime.now())

        val lineFactory = LineFactory(
            linesConfig = LinesConfig(
                LinesAdapter().also {
                    it.setTram(listOf())
                    it.setCable(listOf())
                    it.setDockland(listOf())
                    it.setOverground(listOf())
                    it.setRiver(listOf())
                    it.setUnderground(listOf("src/main/resources/network/underground/$lineName.yml"))
                }
            ),
            timeStep = timeStep,
            transportConfig = transportersConfig
        )

        val line = lineFactory.get().map {
            lineFactory.get(it).also { l ->
                integrationControl.initControl(l)
            }
        }.toList()


        val controller = LineControllerImpl(
            startDelay = startDelay,
            line = line,
            conductor = platformConductor,
            stationChannels = listOf(line).flatten().flatMap { it.stations }.distinct()
                .associateWith { stationService.getChannel(it) }
        )

        val channel = Channel<Transport>()
        val listener = Channel<StationMessage>()

        val job = launch { controller.start(channel) }
        val job3 = launch { stationService.monitor(listener) }

        val running = async {
            integrationControl.status(listener, listOf(job3, job)) { platformConductor.diagnostics() }
        }

    }


}