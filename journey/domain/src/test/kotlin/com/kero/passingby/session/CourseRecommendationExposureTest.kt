package com.kero.passingby.session

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CourseRecommendationExposureTest {
    private val shownAt = 1_700_000_000_000L
    private val weekMillis = 604_800_000L

    @Test fun firstExposureStartsWithTheThirdCompletion() {
        assertFalse(CourseRecommendationExposure(2, shownAt).isEligibleAfter(null))
        assertTrue(CourseRecommendationExposure(3, shownAt).isEligibleAfter(null))
        assertTrue(CourseRecommendationExposure(8, shownAt).isEligibleAfter(null))
    }

    @Test fun laterExposureRequiresBothThreeMoreCompletionsAndAFullWeek() {
        val previous = CourseRecommendationExposure(4, shownAt)
        assertFalse(CourseRecommendationExposure(6, shownAt + weekMillis).isEligibleAfter(previous))
        assertFalse(CourseRecommendationExposure(7, shownAt + weekMillis - 1).isEligibleAfter(previous))
        assertTrue(CourseRecommendationExposure(7, shownAt + weekMillis).isEligibleAfter(previous))
    }

    @Test fun recordingAnExposureBlocksADuplicateAndAClockRollbackCannotSkipTheCooldown() {
        val previous = CourseRecommendationExposure(3, shownAt)
        assertFalse(previous.isEligibleAfter(previous))
        assertFalse(CourseRecommendationExposure(6, shownAt - 1).isEligibleAfter(previous))
    }
}
