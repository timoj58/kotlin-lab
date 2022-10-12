package com.tabiiki.kotlinlab.service

import com.tabiiki.kotlinlab.configuration.StationsConfig
import com.tabiiki.kotlinlab.factory.RouteFactory
import com.tabiiki.kotlinlab.factory.StationFactory
import com.tabiiki.kotlinlab.repo.StationRepoImpl
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock

class RouteServiceImplTest {

    private val stationsConfig = StationsConfig("src/main/resources/network/stations.csv")
    private val stationFactory = StationFactory(stationsConfig)
    private val stationRepo = StationRepoImpl(stationFactory)

    private val routeServiceImpl = RouteServiceImpl(stationRepo, mock(RouteFactory::class.java))

    @Test
    fun `route service generation test` () {
        val route = routeServiceImpl.generate()
        Assertions.assertThat(route.first.trim()).isNotEmpty
        Assertions.assertThat(route.second.trim()).isNotEmpty

        Assertions.assertThat(route.first).isNotEqualTo(route.second)


        Assertions.assertThat(stationRepo.get(route.first)).isNotNull
        Assertions.assertThat(stationRepo.get(route.second)).isNotNull
    }
}