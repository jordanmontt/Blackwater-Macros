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
    fun maintain_1_4to2_0gPerKgBW() {
        val rec = calculateProteinRecommendation(WEIGHT, Goal.MAINTAIN)
        assertThat(rec.basis).isEqualTo(ProteinBasis.BODY_WEIGHT)
        assertProteinRange(rec.range, 112.0, 160.0)
        assertProteinRange(rec.perKg, 1.4, 2.0)
    }

    @Test
    fun surplus_1_6to2_2gPerKgBW() {
        val rec = calculateProteinRecommendation(WEIGHT, Goal.SURPLUS)
        assertProteinRange(rec.range, 128.0, 176.0)
        assertProteinRange(rec.perKg, 1.6, 2.2)
    }

    @Test
    fun cutWithoutBodyFat_1_8to2_7gPerKgBW() {
        val rec = calculateProteinRecommendation(WEIGHT, Goal.CUT)
        assertThat(rec.basis).isEqualTo(ProteinBasis.BODY_WEIGHT)
        assertThat(rec.basisKg).isEqualTo(80.0)
        assertProteinRange(rec.range, 144.0, 216.0)
        assertProteinRange(rec.perKg, 1.8, 2.7)
    }

    @Test
    fun cutWithBodyFat_2_3to3_1gPerKgLeanMass() {
        // 80 kg at 20 % -> 64 kg lean mass -> 147.2–198.4 g
        val rec = calculateProteinRecommendation(WEIGHT, Goal.CUT, 20.0)
        assertThat(rec.basis).isEqualTo(ProteinBasis.LEAN_MASS)
        assertThat(rec.basisKg).isEqualTo(64.0)
        assertProteinRange(rec.range, 147.0, 198.0)
        assertProteinRange(rec.perKg, 2.3, 3.1)
        assertThat(rec.target).isEqualTo(173.0)
    }

    @Test
    fun bodyFatOnlyChangesTheCutRecommendation() {
        assertThat(calculateProteinRecommendation(WEIGHT, Goal.MAINTAIN, 20.0))
            .isEqualTo(calculateProteinRecommendation(WEIGHT, Goal.MAINTAIN))
        assertThat(calculateProteinRecommendation(WEIGHT, Goal.SURPLUS, 20.0).basis).isEqualTo(ProteinBasis.BODY_WEIGHT)
    }

    @Test
    fun ignoresUnusableBodyFatValues() {
        assertThat(calculateProteinRecommendation(WEIGHT, Goal.CUT, 0.0).basis).isEqualTo(ProteinBasis.BODY_WEIGHT)
        assertThat(calculateProteinRecommendation(WEIGHT, Goal.CUT, null).basis).isEqualTo(ProteinBasis.BODY_WEIGHT)
        assertThat(calculateProteinRecommendation(WEIGHT, Goal.CUT, 100.0).basis).isEqualTo(ProteinBasis.BODY_WEIGHT)
    }

    @Test
    fun roundsToNearestInteger() {
        val rec = calculateProteinRecommendation(75.0, Goal.MAINTAIN)
        assertProteinRange(rec.range, 105.0, 150.0)
    }

    @Test
    fun returnsMetadataCorrectly() {
        val rec = calculateProteinRecommendation(82.5, Goal.SURPLUS)
        assertThat(rec.goal).isEqualTo(Goal.SURPLUS)
        assertThat(rec.basisKg).isEqualTo(82.5)
    }

    @Test
    fun targetIsMidpointOfRange() {
        assertThat(calculateProteinRecommendation(WEIGHT, Goal.MAINTAIN).target).isEqualTo(136.0)
        assertThat(calculateProteinRecommendation(WEIGHT, Goal.SURPLUS).target).isEqualTo(152.0)
        assertThat(calculateProteinRecommendation(WEIGHT, Goal.CUT).target).isEqualTo(180.0)
    }

    private fun assertProteinRange(range: ProteinRange, min: Double, max: Double) {
        assertThat(range.min).isEqualTo(min)
        assertThat(range.max).isEqualTo(max)
    }
}
