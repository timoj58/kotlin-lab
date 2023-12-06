package com.tabiiki.kotlinlab.util

import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class HaversineCalculator {

    private val earthRadius = 6371000

    fun distanceBetween(start: Pair<Double, Double>, end: Pair<Double, Double>): Double {
        val phi: Double = Math.toRadians(start.first)
        val phi2: Double = Math.toRadians(end.first)
        val deltaPhi: Double = Math.toRadians(end.first - start.first)
        val deltaLambda: Double = Math.toRadians(end.second - start.second)

        val a =
            sin(deltaPhi / 2) * sin(deltaPhi / 2) + cos(phi) * cos(phi2) * sin(deltaLambda / 2) * sin(deltaLambda / 2)

        val c = 2 * atan2(sqrt(a), sqrt(1 - a))

        return earthRadius * c
    }

    fun calculateBearing(currentLat: Double, currentLong: Double, destLat: Double, destLong: Double): Double {
        val phi: Double = Math.toRadians(currentLat)
        val phi2: Double = Math.toRadians(destLat)
        val lambda: Double = Math.toRadians(currentLong)
        val lambda2: Double = Math.toRadians(destLong)
        val y: Double = sin(lambda2 - lambda) * cos(phi2)
        val x: Double = cos(phi) * sin(phi2) - sin(phi) * cos(phi2) * cos(lambda2 - lambda)
        return Math.toDegrees(atan2(y, x))
    }

    fun getLatitude(latitude: Double, distance: Double, bearing: Double): Double {
        val phi = Math.toRadians(latitude)
        val theta = Math.toRadians(bearing)
        return Math.toDegrees(
            asin(
                sin(phi) * cos(distance / earthRadius) + cos(phi) * sin(
                    distance / earthRadius
                ) * cos(theta)
            )
        )
    }

    fun getLongitude(
        latitude: Double,
        longitude: Double,
        newLatitude: Double,
        distance: Double,
        bearing: Double
    ): Double {
        val lambda: Double = Math.toRadians(longitude)
        val phi: Double = Math.toRadians(latitude)
        val phi2: Double = Math.toRadians(newLatitude)
        val theta: Double = Math.toRadians(bearing)
        return Math.toDegrees(
            lambda + atan2(
                sin(theta) * sin(distance / earthRadius) * cos(phi),
                cos(distance / earthRadius) - sin(phi) * sin(phi2)
            )
        )
    }
}
