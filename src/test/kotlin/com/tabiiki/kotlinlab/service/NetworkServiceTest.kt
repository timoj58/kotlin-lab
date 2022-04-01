package com.tabiiki.kotlinlab.service

import com.tabiiki.kotlinlab.factory.LineFactory
import com.tabiiki.kotlinlab.repo.JourneyRepo
import com.tabiiki.kotlinlab.repo.TransporterTrackerRepo
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import javax.naming.ConfigurationException

internal class NetworkServiceTest{


    @Test
    fun `invalid start delay test`(){
        Assertions.assertThrows(ConfigurationException::class.java) {
            NetworkServiceImpl(80,
                mock(StationService::class.java),
                mock(LineFactory::class.java),
                mock(PlatformConductor::class.java),
                mock(JourneyRepo::class.java),
                mock(TransporterTrackerRepo::class.java))
        }
    }
}