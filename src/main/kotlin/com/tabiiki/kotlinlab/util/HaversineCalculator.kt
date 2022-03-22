package com.tabiiki.kotlinlab.util

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class HaversineCalculator {

    fun distanceBetween(start: Pair<Double, Double>, end: Pair<Double, Double>): Double {
        val earthRadius = 6371000

        val phi: Double = Math.toRadians(start.first)
        val phi2: Double = Math.toRadians(end.first)
        val deltaPhi: Double = Math.toRadians(end.first - start.first)
        val deltaLambda: Double = Math.toRadians(end.second - start.second)

        val a =
            sin(deltaPhi / 2) * Math.sin(deltaPhi / 2) + cos(phi) * cos(phi2) * sin(deltaLambda / 2) * sin(deltaLambda / 2)

        val c = 2 * atan2(sqrt(a), sqrt(1 - a))

        return earthRadius * c
    }
}