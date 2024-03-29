package com.tabiiki.kotlinlab.controller

import com.tabiiki.kotlinlab.service.StationService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

data class StationInformation(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val lines: List<StationLineInformation>
)

data class StationLineInformation(
    val id: String,
    val name: String
)

@RestController
class StationController(
    private val stationService: StationService
) {

    @CrossOrigin(origins = ["http://localhost:3000"])
    @GetMapping("/stations")
    @ResponseStatus(HttpStatus.OK)
    suspend fun getStations(): List<StationInformation> = stationService.getInformation()
}
