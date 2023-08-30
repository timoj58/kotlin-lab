package com.tabiiki.kotlinlab.service

import com.tabiiki.kotlinlab.repo.StationRepo
import com.tabiiki.kotlinlab.util.LineBuilder
import org.junit.jupiter.api.BeforeEach
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

internal class LineConductorTest {

    private val stationRepo = mock(StationRepo::class.java)
    private val lineConductor = LineConductorImpl(mock(PlatformService::class.java))

    @BeforeEach
    fun `init`() {
        `when`(stationRepo.get("A")).thenReturn(
            LineBuilder().stations[0]
        )
        `when`(stationRepo.get("B")).thenReturn(
            LineBuilder().stations[1]
        )
        `when`(
            stationRepo.getNextStationOnLine(
                listOf(),
                Pair("1:A", "B")
            )
        ).thenReturn(
            LineBuilder().stations[2]
        )
    }
}
