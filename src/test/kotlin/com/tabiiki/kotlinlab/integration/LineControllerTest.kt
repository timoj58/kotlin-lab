package com.tabiiki.kotlinlab.integration

import com.tabiiki.kotlinlab.configuration.LinesConfig
import com.tabiiki.kotlinlab.configuration.TransportersConfig
import com.tabiiki.kotlinlab.configuration.adapter.LinesAdapter
import com.tabiiki.kotlinlab.factory.LineFactory
import com.tabiiki.kotlinlab.factory.SignalFactory
import com.tabiiki.kotlinlab.model.Transport
import com.tabiiki.kotlinlab.repo.JourneyRepo
import com.tabiiki.kotlinlab.repo.LineRepoImpl
import com.tabiiki.kotlinlab.repo.StationRepo
import com.tabiiki.kotlinlab.service.LineConductorImpl
import com.tabiiki.kotlinlab.service.LineControllerImpl
import com.tabiiki.kotlinlab.service.PlatformServiceImpl
import com.tabiiki.kotlinlab.service.SectionServiceImpl
import com.tabiiki.kotlinlab.service.SignalServiceImpl
import com.tabiiki.kotlinlab.service.StationMessage
import com.tabiiki.kotlinlab.service.StationService
import com.tabiiki.kotlinlab.service.SwitchService
import com.tabiiki.kotlinlab.util.IntegrationControl
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class LineControllerTest(
    private val startDelay: Long,
    val timeStep: Long,
    private val minimumHold: Int,
    private val transportersConfig: TransportersConfig,
    private val stationService: StationService,
    private val stationRepo: StationRepo,
    private val signalFactory: SignalFactory,
    private val journeyRepo: JourneyRepo,
    private val switchService: SwitchService
) {
    private val integrationControl = IntegrationControl()


    suspend fun test(lineType: String, lineName: String, timeout: Int) = runBlocking {
        val signalService = SignalServiceImpl(signalFactory)
        val sectionService = SectionServiceImpl(45, switchService, signalService, journeyRepo)

        val lineService =
            PlatformServiceImpl(minimumHold, signalService, sectionService, LineRepoImpl(stationRepo), stationRepo)
        val lineConductor = LineConductorImpl(lineService)

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

        val line = lineFactory.get().map {
            lineFactory.get(it).also { l ->
                integrationControl.initControl(l)
            }
        }.toList()


        val controller = LineControllerImpl(
            startDelay = startDelay,
            conductor = lineConductor,
        )

        controller.setStationChannels(
            listOf(line).flatten().flatMap { it.stations }.distinct()
                .associateWith { stationService.getChannel(it) }
        )

        val channel = Channel<Transport>()
        val listener = Channel<StationMessage>()

        val job = launch { controller.start(line, channel) }
        val job3 = launch { stationService.monitor(listener) }

        val running = async {
            integrationControl.status(listener, listOf(job3, job), timeout) { t -> controller.diagnostics(t) }
        }
    }

}