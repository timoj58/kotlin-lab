package com.tabiiki.kotlinlab.service

import com.tabiiki.kotlinlab.configuration.StationsConfig
import com.tabiiki.kotlinlab.factory.RouteFactory
import com.tabiiki.kotlinlab.factory.StationFactory
import com.tabiiki.kotlinlab.repo.StationRepoImpl
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import kotlin.random.Random.Default.nextBoolean

class RouteServiceImplTest {

    private val stationsConfig = StationsConfig("src/main/resources/network/stations.csv")
    private val stationFactory = StationFactory(stationsConfig)
    private val stationRepo = StationRepoImpl(stationFactory)
    private val routeFactory = mock(RouteFactory::class.java)

    private val routeServiceImpl = RouteServiceImpl(stationRepo, routeFactory)

    @Test
    fun `route service generation test` () {

        stationRepo.get().forEach {
            `when`(routeFactory.isSelectableStation(it.id)).thenReturn(nextBoolean())
        }

        val route = routeServiceImpl.generate()
        Assertions.assertThat(route.first.trim()).isNotEmpty
        Assertions.assertThat(route.second.trim()).isNotEmpty

        Assertions.assertThat(route.first).isNotEqualTo(route.second)


        Assertions.assertThat(stationRepo.get(route.first)).isNotNull
        Assertions.assertThat(stationRepo.get(route.second)).isNotNull
    }

}