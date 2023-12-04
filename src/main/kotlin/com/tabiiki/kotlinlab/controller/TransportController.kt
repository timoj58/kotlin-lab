package com.tabiiki.kotlinlab.controller

import com.tabiiki.kotlinlab.service.LineService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

data class TransporterInformation(
    val id: UUID,
    val type: String,
)

@RestController
class TransportController(
    private val lineService: LineService
) {

    @CrossOrigin(origins = ["http://localhost:3000"])
    @GetMapping("/transporters")
    @ResponseStatus(HttpStatus.OK)
    suspend fun getTransporters(): List<TransporterInformation> = lineService.getTransporterInformation()
}