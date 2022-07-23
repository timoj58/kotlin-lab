package com.tabiiki.kotlinlab.monitor

import com.tabiiki.kotlinlab.factory.SignalMessage
import com.tabiiki.kotlinlab.factory.SignalValue
import com.tabiiki.kotlinlab.repo.LineDirection
import com.tabiiki.kotlinlab.repo.LineRepo
import com.tabiiki.kotlinlab.service.SectionService
import com.tabiiki.kotlinlab.service.SignalService
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch


class PlatformMonitor(
    private val sectionService: SectionService,
    private val signalService: SignalService,
    private val lineRepo: LineRepo
) {
    suspend fun monitorPlatform(key: Pair<String, String>) = coroutineScope {
        var previousSignal: SignalMessage? = null
        val isTerminal = key.first.contains(LineDirection.TERMINAL.toString())
        val terminalSection = terminalSection(key)

        do {
            signalService.receive(key)?.let {
                if (previousSignal == null || it != previousSignal) {
                    previousSignal = it

                    when (it.signalValue) {
                        SignalValue.RED -> processRed(it, key, isTerminal, terminalSection)
                        SignalValue.GREEN -> processGreen(it, key, isTerminal, terminalSection)
                    }
                }
            }

        } while (true)
    }

    private suspend fun processGreen(
        signal: SignalMessage,
        key: Pair<String, String>,
        isTerminal: Boolean,
        terminalSection: Pair<String, String>
    ) =
        coroutineScope {
            val sections = if (isTerminal) listOf(
                Pair(
                    terminalSection,
                    sectionService.isClearWithPriority(terminalSection)
                )
            )
            else lineRepo.getPreviousSections(key).map { Pair(it, sectionService.isClearWithPriority(it)) }
            val priority = sections.filter { it.second.second != 0 }.sortedByDescending { it.second.second }
                .firstOrNull { !it.second.first }

            priority?.let {
                launch {
                    signalService.send(
                        it.first,
                        SignalMessage(signalValue = SignalValue.GREEN, key = signal.key)
                    )
                }
            }

            sections.filter { priority == null || it.second.first }.forEach { section ->
                launch {
                    signalService.send(
                        section.first,
                        SignalMessage(signalValue = SignalValue.GREEN, key = signal.key)
                    )
                }
            }
        }

    private suspend fun processRed(
        signal: SignalMessage,
        key: Pair<String, String>,
        isTerminal: Boolean,
        terminalSection: Pair<String, String>
    ) =
        coroutineScope {
            val sections = if (isTerminal) listOf(terminalSection) else lineRepo.getPreviousSections(key)
            sections.forEach {
                launch {
                    signalService.send(
                        it,
                        SignalMessage(signalValue = SignalValue.RED, key = signal.key, id = signal.id)
                    )
                }
            }

        }

    companion object {
        private fun terminalSection(key: Pair<String, String>) = Pair(
            "${key.first.substringBefore(":")}:${key.second.substringAfter(":")}",
            "${key.second.substringAfter(":")}|"
        )
    }

}