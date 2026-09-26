package com.blackwatermacros.app.core

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Kotlin JUnit mirror of `tests/unit/protein.test.ts`. Option A contract:
 * this file must stay in lockstep with the TS spec.
 */
class ProteinTest {

    private val WEIGHT = 80.0

    @Test
    fun maintain_1_2to1_6gPerKgBW() {
        val rec = calculateProteinRecommendation(WEIGHT, Goal.MAINTAIN)
        assertProteinRange(rec.bwRange, 96.0, 128.0)
        assertProteinRange(rec.bwPerKg, 1.2, 1.6)
    }

    @Test
    fun surplus_1_6to2_0gPerKgBW() {
        val rec = calculateProteinRecommendation(WEIGHT, Goal.SURPLUS)
        assertProteinRange(rec.bwRange, 128.0, 160.0)
        assertProteinRange(rec.bwPerKg, 1.6, 2.0)
    }

    @Test
    fun cut_1_6to2_2gPerKgBW() {
        val rec = calculateProteinRecommendation(WEIGHT, Goal.CUT)
        assertProteinRange(rec.bwRange, 128.0, 176.0)
        assertProteinRange(rec.bwPerKg, 1.6, 2.2)
    }

    @Test
    fun roundsToNearestInteger() {
        val rec = calculateProteinRecommendation(75.0, Goal.MAINTAIN)
        assertProteinRange(rec.bwRange, 90.0, 120.0)
    }

    @Test
    fun returnsMetadataCorrectly() {
        val rec = calculateProteinRecommendation(82.5, Goal.SURPLUS)
        assertThat(rec.goal).isEqualTo(Goal.SURPLUS)
        assertThat(rec.bodyWeightKg).isEqualTo(82.5)
    }

    @Test
    fun targetIsMidpointOfRange() {
        assertThat(calculateProteinRecommendation(WEIGHT, Goal.MAINTAIN).target).isEqualTo(112.0)
        assertThat(calculateProteinRecommendation(WEIGHT, Goal.SURPLUS).target).isEqualTo(144.0)
        assertThat(calculateProteinRecommendation(WEIGHT, Goal.CUT).target).isEqualTo(152.0)
    }

    private fun assertProteinRange(range: ProteinRange, min: Double, max: Double) {
        assertThat(range.min).isEqualTo(min)
        assertThat(range.max).isEqualTo(max)
    }
}