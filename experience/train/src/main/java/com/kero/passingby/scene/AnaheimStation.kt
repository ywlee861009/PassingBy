package com.kero.passingby.scene

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** ARTIC's tall glazed north entrance and translucent steel diagrid vault, seen obliquely. */
internal fun anaheimArticBitmap(): Bitmap {
    val bitmap = Bitmap.createBitmap(145, 63, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint()
    val path = Path()
    val steel = Color.rgb(107, 135, 145)
    val rim = Color.rgb(206, 221, 218)

    // Each successive arch is smaller toward the low south end. Rows wrap across the vault.
    fun x(depth: Float, angle: Double) = 33f + 84f * depth + (31f - 6f * depth) * sin(angle).toFloat()
    fun y(depth: Float, angle: Double) = 60f - 3f * depth - (57f - 25f * depth) * cos(angle).toFloat()
    val rows = 6
    val bays = 6
    for (row in 0 until rows) {
        val upper = PI / 2 * row / rows
        val lower = PI / 2 * (row + 1) / rows
        val middle = (upper + lower) / 2
        val shade = row.toFloat() / rows
        for (bay in 0 until bays) {
            val a = bay.toFloat() / bays
            val b = (bay + 1f) / bays
            val midBay = (a + b) / 2
            path.rewind()
            path.moveTo(x(a, upper), y(a, upper))
            path.lineTo(x(b, upper), y(b, upper))
            path.lineTo(x(b, lower), y(b, lower))
            path.lineTo(x(a, lower), y(a, lower))
            path.close()
            // Pale cushions catch sky light on the crown; lower panels carry a muted blue tint.
            paint.color = mix(Color.rgb(222, 233, 224), Color.rgb(116, 155, 176), shade)
            canvas.drawPath(path, paint)
            paint.color = steel
            for (depth in listOf(a, b)) {
                canvas.drawLine(x(depth, upper), y(depth, upper), x(midBay, middle), y(midBay, middle), paint)
                canvas.drawLine(x(midBay, middle), y(midBay, middle), x(depth, lower), y(depth, lower), paint)
            }
        }
    }

    path.rewind()
    path.moveTo(2f, 60f)
    for (step in 0..32) {
        val angle = -PI / 2 + PI * step / 32
        path.lineTo(x(0f, angle), y(0f, angle))
    }
    path.close()
    paint.color = Color.rgb(101, 144, 154)
    canvas.drawPath(path, paint)
    canvas.save()
    canvas.clipPath(path)
    paint.color = Color.rgb(155, 187, 188)
    canvas.drawRect(0f, 6f, 65f, 24f, paint)
    paint.color = Color.rgb(119, 159, 168)
    canvas.drawRect(0f, 24f, 65f, 37f, paint)
    paint.color = Color.rgb(74, 103, 111)
    for (mullion in 6..64 step 8) canvas.drawLine(mullion.toFloat(), 2f, mullion.toFloat(), 60f, paint)
    for (transom in 10..58 step 8) canvas.drawLine(0f, transom.toFloat(), 65f, transom.toFloat(), paint)
    // A few interior lights along the mezzanine and doors survive the common night tint.
    paint.color = Color.rgb(227, 197, 146)
    for (light in 12..54 step 8) canvas.drawRect(light.toFloat(), 44f, light + 3f, 46f, paint)
    canvas.restore()
    paint.color = rim
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = 2f
    canvas.drawPath(path, paint)
    paint.style = Paint.Style.FILL
    paint.color = Color.rgb(72, 94, 101)
    canvas.drawRect(20f, 51f, 46f, 60f, paint)
    paint.color = Color.rgb(182, 172, 147)
    for (door in 22..42 step 7) canvas.drawRect(door.toFloat(), 53f, door + 4f, 60f, paint)
    paint.color = rim
    canvas.drawRect(1f, 61f, 68f, 63f, paint)
    return bitmap
}
