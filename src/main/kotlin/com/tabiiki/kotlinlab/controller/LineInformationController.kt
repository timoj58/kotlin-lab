package com.tabiiki.kotlinlab.controller

import com.tabiiki.kotlinlab.service.LineService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

data class LineStation(
    val id: String,
    val name : String,
    val latitude: Double,
    val longitude: Double
)

data class LineInformation(
    val name: String,
    val id: String,
    val stations: List<LineStation>
)

@RestController
class LineInformationController(
    private val lineService: LineService
) {

    @CrossOrigin(origins = ["http://localhost:3000"])
    @GetMapping("/lines")
    @ResponseStatus(HttpStatus.OK)
    suspend fun getLines(): List<LineInformation> = lineService.getLineInformation()
}
