package com.kero.passingby.session

data class CourseRecommendationExposure(val completedJourneys: Int, val shownAtMillis: Long) {
    fun isEligibleAfter(previous: CourseRecommendationExposure?): Boolean {
        if (completedJourneys < MINIMUM_COMPLETIONS) return false
        if (previous == null) return true
        return completedJourneys - previous.completedJourneys >= MINIMUM_COMPLETIONS &&
            shownAtMillis - previous.shownAtMillis >= COOLDOWN_MILLIS
    }

    private companion object {
        const val MINIMUM_COMPLETIONS = 3
        const val COOLDOWN_MILLIS = 7 * 24 * 60 * 60 * 1000L
    }
}
