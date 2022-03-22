package com.tabiiki.kotlinlab.util

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

internal class HaversineCalculatorTest{

    private val haversineCalculator = HaversineCalculator()

    @Test
    fun `calculate distance between two points`(){

        val distance = haversineCalculator.distanceBetween(
            Pair(50.05983333333333,-5.708833333333334),
            Pair(58.63966666666666, -3.0686666666666667))
        //online reference
        assertThat(kotlin.math.ceil(distance / 1000)).isEqualTo(970.0)
    }

    @Test
    fun `calculate distance between Stratford and North Greenwich as the crow flies`(){
        val distance = haversineCalculator.distanceBetween(
            Pair(51.541692575874,-0.00375164102719075),
            Pair(51.5002551610895,0.00358625912595083))
        //test data, seems legit.  i guessed at 5.  it is.
        assertThat(kotlin.math.ceil(distance / 1000)).isEqualTo(5.0)
    }

}