package com.tabiiki.kotlinlab.monitor

import com.tabiiki.kotlinlab.configuration.LinesConfig
import com.tabiiki.kotlinlab.configuration.TransportConfig
import com.tabiiki.kotlinlab.configuration.TransportersConfig
import com.tabiiki.kotlinlab.configuration.adapter.LinesAdapter
import com.tabiiki.kotlinlab.configuration.adapter.TransportersAdapter
import com.tabiiki.kotlinlab.factory.LineFactory
import com.tabiiki.kotlinlab.factory.SignalFactory
import com.tabiiki.kotlinlab.factory.SignalMessage
import com.tabiiki.kotlinlab.factory.SignalValue
import com.tabiiki.kotlinlab.service.SignalService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.util.UUID

class PlatformMonitorTest {

    private val key = Pair("Tram:260", "20")

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
    private val lineFactory = LineFactory(3, transportConfig, linesConfig)
    private val signalFactory = SignalFactory(lineFactory)
    private val signalService = SignalService(signalFactory)

    private val platformMonitor = PlatformMonitor(
        signalService = signalService
    )

    @BeforeEach
    fun `init`() {
        platformMonitor.init(key = key)
    }

    @Disabled("found issue - very hard to make a test for it due to timing")
    @Test
    fun `work out how to get lock exception - and then fix code`(): Unit = runBlocking {
        launch { signalService.init(key = key) }
        launch { platformMonitor.monitorPlatform(key = key) }

        delay(100)
        val id = UUID.randomUUID()
        val id2 = UUID.randomUUID()

        signalService.send(
            key = key,
            signalMessage = SignalMessage(
                id = id,
                signalValue = SignalValue.RED
            )
        )

        delay(100)

        signalService.send(
            key = key,
            signalMessage = SignalMessage(
                id = id,
                signalValue = SignalValue.GREEN
            )
        )

        delay(1)

        //  delay(1)

        while (!platformMonitor.isClear(key)) {
            delay(1)
        }

        signalService.send(
            key = key,
            signalMessage = SignalMessage(
                id = id2,
                signalValue = SignalValue.RED
            )
        )

        delay(1000)

        Assertions.assertThat(true).isFalse()
    }
}
