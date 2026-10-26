package com.kero.passingby.experience.ship

import android.graphics.*
import com.kero.passingby.experience.*
import com.kero.passingby.experience.voyage.*
import com.kero.passingby.session.JourneyStore
import kotlin.math.*

class ShipExperienceProvider : ExperienceProvider {
    override val id = "ship"
    override val mode = JourneyMode.SHIP
    override val courses = listOf(VoyageCourse("ship_busan_jeju", 301, R.string.ship_region, R.string.ship_vehicle,
        listOf(VoyageStop(R.string.ship_busan, "Busan", "Busan"), VoyageStop(R.string.ship_strait, "Korea Strait", "Korea Strait"), VoyageStop(R.string.ship_jeju, "Jeju", "Jeju"))))
    override fun create(store: JourneyStore) = VoyageProgram(store, courses, ::ShipRenderer, AmbientLoop.Kind.SHIP)
}

class ShipRenderer(scene: VoyageScene) : VoyageRenderer(scene) {
    override val roundness = .4f
    override fun drawWorld(canvas: Canvas, width: Float, height: Float) {
        val night = scene.timeOfDay == TimeOfDay.NIGHT
        val dusk = scene.timeOfDay == TimeOfDay.DUSK || scene.timeOfDay == TimeOfDay.DAWN
        fill(canvas, width, height, if (night) 0xff101d36.toInt() else 0xff739faf.toInt(),
            if (night) 0xff4b556c.toInt() else if (dusk) 0xffe7b8a1.toInt() else 0xffdbe0d4.toInt())
        val horizon = height * .46f + sin(scene.distance * 18).toFloat() * height * .006f
        paint.color = if (night) 0xffe0e6e9.toInt() else 0xfff7dfb1.toInt()
        canvas.drawCircle(width * .7f, height * .27f, min(width, height) * .045f, paint)
        paint.shader = LinearGradient(0f, horizon, 0f, height,
            if (night) 0xff324d64.toInt() else 0xff628f99.toInt(), 0xff1e424f.toInt(), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, horizon, width, height, paint); paint.shader = null
        paint.color = if (night) 0xff263c4c.toInt() else 0xff657c78.toInt()
        for (i in 0..3) {
            val x = (((i * .36f - scene.distance.toFloat() * .09f) % 1.5f + 1.5f) % 1.5f - .2f) * width
            path.rewind(); path.moveTo(x - width * .13f, horizon)
            path.cubicTo(x - width * .07f, horizon - height * .08f, x, horizon - height * .09f, x + width * .12f, horizon)
            path.close(); canvas.drawPath(path, paint)
        }
        paint.style = Paint.Style.STROKE
        for (row in 0..25) {
            val depth = row / 25f
            val y = horizon + depth * depth * (height - horizon)
            paint.color = Color.argb((18 + depth * 45).toInt(), 184, 212, 210)
            paint.strokeWidth = 1f + depth * 1.5f
            path.rewind(); path.moveTo(0f, y)
            for (x in 0..40) {
                val xx = width * x / 40
                val yy = y + sin(x * .65 + scene.distance * 90 + row) * height * .004 * depth
                path.lineTo(xx, yy.toFloat())
            }
            canvas.drawPath(path, paint)
        }
        paint.style = Paint.Style.FILL
    }
}
