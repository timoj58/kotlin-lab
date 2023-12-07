package com.tabiiki.kotlinlab.integration

import com.tabiiki.kotlinlab.configuration.LinesConfig
import com.tabiiki.kotlinlab.configuration.TransportersConfig
import com.tabiiki.kotlinlab.configuration.adapter.LinesAdapter
import com.tabiiki.kotlinlab.factory.LineFactory
import com.tabiiki.kotlinlab.factory.SignalFactory
import com.tabiiki.kotlinlab.factory.StationFactory
import com.tabiiki.kotlinlab.model.Commuter
import com.tabiiki.kotlinlab.repo.LineRepo
import com.tabiiki.kotlinlab.repo.StationRepo
import com.tabiiki.kotlinlab.service.LineConductor
import com.tabiiki.kotlinlab.service.LineController
import com.tabiiki.kotlinlab.service.PlatformServiceV2
import com.tabiiki.kotlinlab.service.SectionServiceV2
import com.tabiiki.kotlinlab.service.SignalService
import com.tabiiki.kotlinlab.service.StationMessage
import com.tabiiki.kotlinlab.service.StationService
import com.tabiiki.kotlinlab.service.SwitchService
import com.tabiiki.kotlinlab.util.IntegrationControl
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class LineControllerTest(
    val timeStep: Long,
    private val transportersConfig: TransportersConfig,
    private val stationRepo: StationRepo,
    private val signalFactory: SignalFactory,
    private val switchService: SwitchService,
    private val stationFactory: StationFactory
) {
    private val integrationControl = IntegrationControl()

    suspend fun test(lineType: String, lineName: String, timeout: Int) = runBlocking {
        val signalService = SignalService(signalFactory)
        val sectionService = SectionServiceV2(switchService, signalService)
        val lineFactory = LineFactory(
            linesConfig = LinesConfig(
                LinesAdapter().also {
                    it.setTram(listOf())
                    it.setCable(listOf())
                    it.setDockland(listOf())
                    it.setOverground(listOf())
                    it.setRiver(listOf())
                    it.setUnderground(listOf("src/main/resources/network/$lineType/$lineName.yml"))
                }
            ),
            timeStep = timeStep,
            transportConfig = transportersConfig
        )
        val lineService =
            PlatformServiceV2(
                sectionService,
                signalService,
                lineFactory,
                LineRepo(stationRepo)
            )
        val lineConductor = LineConductor(lineService)

        val stationService =
            StationService(timeStep = timeStep, signalService = signalService, stationFactory = stationFactory, lineFactory = lineFactory)

        val line = lineFactory.get().map {
            lineFactory.get(it).also { l ->
                integrationControl.initControl(l)
            }
        }.toList()

        val controller = LineController(
            conductor = lineConductor
        )

        val globalCommuterChannel = Channel<Commuter>()
        val initJob = launch { controller.init(globalCommuterChannel) }

        delay(2000)

        val listener = Channel<StationMessage>()

        val job = launch { controller.start(line) }

        val job2 = launch {
            delay(100)
            stationService.start(listener, globalCommuterChannel, line.first().name)
        }

        val running = async {
            integrationControl.status(listener, listOf(initJob, job, job2), timeout) {}
        }
    }
}
