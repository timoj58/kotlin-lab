package com.tabiiki.kotlinlab.service

import com.tabiiki.kotlinlab.configuration.LinesConfig
import com.tabiiki.kotlinlab.configuration.StationsConfig
import com.tabiiki.kotlinlab.configuration.TransportConfig
import com.tabiiki.kotlinlab.configuration.TransportersConfig
import com.tabiiki.kotlinlab.configuration.adapter.LinesAdapter
import com.tabiiki.kotlinlab.configuration.adapter.TransportersAdapter
import com.tabiiki.kotlinlab.factory.LineFactory
import com.tabiiki.kotlinlab.factory.SignalFactoryV2
import com.tabiiki.kotlinlab.factory.SignalMessageV2
import com.tabiiki.kotlinlab.factory.SignalValue
import com.tabiiki.kotlinlab.factory.StationFactory
import com.tabiiki.kotlinlab.repo.StationRepo
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test

class SignalServiceV2Test {
    private val stationsConfig = StationsConfig("src/main/resources/network/stations.csv")
    private val stationFactory = StationFactory(stationsConfig)
    private val stationRepo = StationRepo(stationFactory)
    private val linesAdapter = LinesAdapter(
        listOf(
            "src/main/resources/network/overground/elizabeth.yml",
            "src/main/resources/network/tram/tram.yml",
            "src/main/resources/network/river/river.yml",
            "src/main/resources/network/underground/jubilee.yml",
            "src/main/resources/network/dockland/dlr.yml",
            "src/main/resources/network/underground/circle.yml"
        ),
        listOf(),
        listOf(),
        listOf(),
        listOf(),
        listOf()
    )

//    private val lineRepo = LineRepo(stationRepo)
    private val linesConfig = LinesConfig(linesAdapter)

    private val transportersAdapter = TransportersAdapter(
        IntRange(1, 7).map {
            TransportConfig(
                transportId = it,
                capacity = 1000,
                weight = 1500,
                topSpeed = 20,
                power = 2300
            )
        }.toList()

    )
    private val transportConfig = TransportersConfig(transportersAdapter)

    private val lineFactory = LineFactory(
        timeStep = 3L,
        transportConfig = transportConfig,
        linesConfig = linesConfig
    )

    private val lineService = LineService(lineFactory, stationRepo)

    private val signalFactory = SignalFactoryV2()

    private val signalServiceV2 = SignalServiceV2(3L)

    @Test
    fun `subscribe to a signal receive a message then close`() = runBlocking {
        val channel = Channel<SignalMessageV2>()

        signalServiceV2.init(
            lines = lineFactory.get().map { lineFactory.get(it) },
            isSwitchStation = { l, s -> lineService.isSwitchStation(l, s) },
            previousSections = { k -> lineService.getPreviousSections(k) }
        )

        val job = launch { signalServiceV2.monitor() }
        val job2 = launch { signalServiceV2.subscribe(key = Pair("Elizabeth:2", "650"), channel = channel) }
        Assertions.assertThat(channel.receive().signalValue).isEqualTo(SignalValue.GREEN)

        val job3 = launch {
            signalServiceV2.send(
                key = Pair("Elizabeth:2", "650"),
                message = SignalMessageV2(
                    signalValue = SignalValue.RED,
                    line = null
                )
            )
        }
        delay(100)
        Assertions.assertThat(channel.receive().signalValue).isEqualTo(SignalValue.RED)

        job.cancel()
        job2.cancel()
        job3.cancel()
    }
}
