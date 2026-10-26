package com.kero.passingby.experience.airplane

import android.graphics.*
import com.kero.passingby.experience.*
import com.kero.passingby.experience.voyage.*
import com.kero.passingby.session.JourneyStore
import kotlin.math.*

class AirplaneExperienceProvider : ExperienceProvider {
    override val id = "airplane"
    override val mode = JourneyMode.AIRPLANE
    override val courses = listOf(VoyageCourse("air_incheon_jeju", 201, R.string.air_region, R.string.air_vehicle,
        listOf(VoyageStop(R.string.air_incheon, "Incheon", "Incheon"), VoyageStop(R.string.air_yellow_sea, "Yellow Sea", "Yellow Sea"), VoyageStop(R.string.air_jeju, "Jeju", "Jeju"))))
    override fun create(store: JourneyStore) = VoyageProgram(store, courses, ::AirplaneRenderer, AmbientLoop.Kind.AIRPLANE)
}

class AirplaneRenderer(scene: VoyageScene) : VoyageRenderer(scene) {
    override val roundness = .34f
    override fun drawWorld(canvas: Canvas, width: Float, height: Float) {
        val night = scene.timeOfDay == TimeOfDay.NIGHT
        val dusk = scene.timeOfDay == TimeOfDay.DUSK || scene.timeOfDay == TimeOfDay.DAWN
        fill(canvas, width, height, if (night) 0xff10233f.toInt() else 0xff5e95c1.toInt(),
            if (night) 0xff435375.toInt() else if (dusk) 0xffecc9b4.toInt() else 0xffc9e1ed.toInt())
        paint.color = if (night) 0xffd7e4ef.toInt() else 0xfffff2d7.toInt()
        canvas.drawCircle(width * .72f, height * .23f, min(width, height) * .035f, paint)
        paint.color = if (night) 0xff1d3b56.toInt() else 0xff678f9e.toInt()
        canvas.drawRect(0f, height * .64f, width, height, paint)
        for (layer in 0..2) {
            paint.color = if (night) 0xff65748c.toInt() else intArrayOf(0xffc5d6df.toInt(), 0xffe0e8eb.toInt(), 0xfff1f3f0.toInt())[layer]
            repeat(9) { i ->
                val x = (((i / 8f - scene.distance.toFloat() * (.12f + layer * .13f)) % 1.4f + 1.4f) % 1.4f - .2f) * width
                val y = height * (.55f + layer * .15f) + sin(i * 2f) * height * .045f
                canvas.drawOval(x - width * .18f, y - height * .035f, x + width * .18f, y + height * .08f, paint)
            }
        }
        // Wing view stays attached to the aircraft while the clouds drift underneath.
        paint.color = if (night) 0xff7b899a.toInt() else 0xffd5dedf.toInt()
        path.rewind(); path.moveTo(width, height * .84f); path.lineTo(width * .38f, height * .54f)
        path.lineTo(width * .29f, height * .4f); path.lineTo(width * .42f, height * .44f)
        path.lineTo(width, height * .66f); path.close(); canvas.drawPath(path, paint)
        paint.color = 0x55717c89; paint.strokeWidth = 1.5f
        canvas.drawLine(width * .45f, height * .54f, width, height * .76f, paint)
    }
}
