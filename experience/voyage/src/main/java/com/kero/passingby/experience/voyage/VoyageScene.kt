package com.kero.passingby.experience.voyage

import com.kero.passingby.experience.*

class VoyageScene(var course: VoyageCourse) : ExperienceScene {
    var progress = 0f
    var durationSeconds = 1500.0
    var direction = 1
    var eventSeed = 0
    val routePosition get() = if (direction > 0) progress else 1f - progress
    var distance = 0.0
    var travelSpeed = 0.0
    var fixedTime: TimeOfDay? = null
    var windowFrame = true
    var viewportFromEnd = false
    var viewportEdge = 0f
    override val distanceU get() = distance
    override val speed get() = travelSpeed
    override val season = Season.SUMMER
    override var timeOfDay = TimeOfDay.DAY
        private set
    override val autoWeather = false
    override val rainAmount = 0f
    override val snowAmount = 0f
    override val urbanDensity = 0f
    override val panelColor get() = if (!course.supportsTimeOfDay) 0xff151e30.toInt()
        else if (timeOfDay == TimeOfDay.NIGHT) 0xff293447.toInt() else 0xffc3c7c5.toInt()
    override fun paperColor(baseColor: Int) = if (!course.supportsTimeOfDay) 0xffd4dce3.toInt()
        else if (timeOfDay == TimeOfDay.NIGHT) 0xffd1c9be.toInt() else baseColor
    override fun setViewport(fromEnd: Boolean, edgePx: Float) { viewportFromEnd = fromEnd; viewportEdge = edgePx }
    override fun snapTimeOfDay() = resolveTime()
    override fun snapWeather() = Unit
    fun resolveTime() {
        timeOfDay = if (!course.supportsTimeOfDay) TimeOfDay.NIGHT else fixedTime ?: when {
            progress < .2f -> TimeOfDay.DAWN
            progress < .65f -> TimeOfDay.DAY
            progress < .85f -> TimeOfDay.DUSK
            else -> TimeOfDay.NIGHT
        }
    }
}
