package com.kero.passingby.experience.spaceship

import android.graphics.*
import com.kero.passingby.experience.voyage.*
import kotlin.math.*
import kotlin.random.Random

/** Ground launch, vertical climb, gravity turn and deep-space flybys sampled from one clock. */
class SpaceRenderer(scene: VoyageScene) : VoyageRenderer(scene) {
    private val images = PlanetGlobes()
    private val distantImages = PlanetGlobes(size = 192, capacity = 3)
    private val passingWorld = SpaceScenery.World()
    private val meteor = SpaceScenery.Meteor()
    private val landscape = EarthLaunchLandscape()
    private val starData = FloatArray(180 * 4).apply {
        val random = Random(108)
        for (i in indices step 4) {
            this[i] = random.nextFloat(); this[i + 1] = random.nextFloat()
            this[i + 2] = random.nextFloat(); this[i + 3] = random.nextFloat()
        }
    }
    private val ring = RectF()
    override val roundness = .12f
    override val bitmapBytes: Long get() = images.bytes + distantImages.bytes

    override fun drawWorld(canvas: Canvas, width: Float, height: Float) {
        val course = scene.course as SpaceCourse
        val view = SpaceFlightPath.sample(scene.routePosition, scene.durationSeconds, course.stations.size)
        val unit = min(width, height)
        val darkness = SpaceFlightPath.smooth((view.ascent - .12f) / .88f)
        fill(canvas, width, height,
            mix(mix(0xffa37a4d.toInt(), 0xff3c779f.toInt(), view.ascent), 0xff02050c.toInt(), darkness),
            mix(mix(0xffebbe88.toInt(), 0xffb0d1e4.toInt(), SpaceFlightPath.smooth(view.ascent / .5f)), 0xff0a1320.toInt(), darkness))
        stars(canvas, width, height, view)

        if (view.turn < 1f) {
            // Retain only the globe needed for the launch, including after a seek back.
            images.retain(CelestialBody.EARTH)
            distantImages.close()
            // Before launch completes there is deliberately no horizontal camera movement.
            val radius = unit * lerp(3f, .42f, view.turn)
            val top = lerp(height * .64f, height * .55f - unit * .42f, view.turn)
            val earthVisibility = SpaceFlightPath.smooth((view.ascent - .38f) / .62f)
            globe(canvas, CelestialBody.EARTH, width * lerp(.5f, .4f, view.turn),
                top + radius, radius, earthVisibility)
            landscape.draw(canvas, width, height, view.ascent)
            landscape.clouds(canvas, width, height, view.ascent)
        } else {
            val position = view.trackPosition
            passingWorlds(canvas, width, height, position)
            shootingStars(canvas, width, height)
            course.bodies.forEachIndexed { index, body ->
                val offset = index - position
                if (offset in -.6f.. .6f) {
                    val scale = when (body) {
                        CelestialBody.JUPITER -> 1.13f
                        CelestialBody.SATURN -> .88f
                        else -> 1f
                    }
                    val radius = unit * (.045f + .375f * exp(-abs(offset) * 4.6f)) * scale
                    // Changing apparent size and vertical position conveys a three-dimensional flyby.
                    val x = width * (.4f + offset * 2.4f)
                    val y = height * (.55f + offset * .075f + sin(index * 1.7f) * .045f)
                    if (x - radius * 2.5f < width)
                        globe(canvas, body, x, y, radius, 1f)
                }
            }
            val leg = position.toInt().coerceIn(0, course.bodies.lastIndex - 1)
            if (course.bodies[leg] == CelestialBody.MOON && course.bodies[leg + 1] == CelestialBody.JUPITER)
                asteroids(canvas, width, height, (position - leg).coerceIn(0f, 1f))
        }
        // Very faint window reflections; they do not obscure surface details.
        paint.shader = null; paint.style = Paint.Style.FILL; paint.color = 0x04d3e8f7
        path.rewind(); path.moveTo(width * .1f, 0f); path.lineTo(width * .115f, 0f)
        path.lineTo(width * .38f, height); path.lineTo(width * .35f, height); path.close()
        canvas.drawPath(path, paint)
    }

    private fun passingWorlds(canvas: Canvas, width: Float, height: Float, position: Float) {
        val course = scene.course as SpaceCourse
        val leg = position.toInt().coerceIn(course.bodies.indices)
        // These are scenery between the four visits, so they add no map markers or countdowns.
        if (leg == 0 || leg >= course.bodies.lastIndex) return
        val visibility = SpaceFlightPath.smooth((abs(position - round(position)) - .18f) / .18f)
        if (visibility <= 0f) return
        val seconds = scene.routePosition * scene.durationSeconds
        for (lane in 0 until SpaceScenery.WORLD_LANES) {
            val body = when (course.bodies[leg]) {
                CelestialBody.MOON -> if (lane == 0) CelestialBody.MARS else CelestialBody.MOON
                CelestialBody.SATURN -> if (lane == 0) CelestialBody.URANUS else CelestialBody.NEPTUNE
                else -> CelestialBody.MOON
            }
            SpaceScenery.worldAt(seconds, lane, passingWorld)
            globe(canvas, body, passingWorld.x * width, passingWorld.y * height,
                passingWorld.radius * min(width, height), passingWorld.opacity * visibility, distantImages)
        }
    }

    private fun shootingStars(canvas: Canvas, width: Float, height: Float) {
        val unit = min(width, height)
        val seconds = scene.routePosition * scene.durationSeconds
        for (lane in 0 until SpaceScenery.METEOR_LANES) {
            SpaceScenery.meteorAt(seconds, lane, meteor)
            if (meteor.opacity <= 0f) continue
            val x = meteor.x * width; val y = meteor.y * height
            val length = meteor.length * unit
            paint.style = Paint.Style.STROKE
            paint.strokeCap = Paint.Cap.ROUND
            // Short, tapered tails keep these occasional glints smaller than the visiting worlds.
            for (part in 0 until 8) {
                val near = part / 8f; val far = (part + 1) / 8f
                paint.color = Color.argb((meteor.opacity * (1f - near).pow(2) * 195).toInt(), 192, 220, 248)
                paint.strokeWidth = unit * .0018f * (1f - near * .8f)
                canvas.drawLine(x + length * near, y - length * near * .36f,
                    x + length * far, y - length * far * .36f, paint)
            }
            paint.style = Paint.Style.FILL
            paint.color = Color.argb((meteor.opacity * 235).toInt(), 234, 245, 255)
            canvas.drawCircle(x, y, (unit * .0015f).coerceAtLeast(.7f), paint)
            paint.strokeCap = Paint.Cap.BUTT
        }
    }

    private fun stars(canvas: Canvas, width: Float, height: Float, view: SpaceFlightPath.View) {
        val unit = min(width, height)
        // Seconds keep the apparent cruise speed brisk even during a 90-minute focus session.
        val seconds = scene.progress * scene.durationSeconds
        for (i in starData.indices step 4) {
            val depth = starData[i + 2]
            val driftX = (seconds * (.035 + depth * .085)).toFloat() * view.turn
            val driftY = view.ascent * .018f + (seconds * (.006 + depth * .014)).toFloat() * view.turn
            val x = ((starData[i] - driftX) % 1f + 1f) % 1f * width
            val y = ((starData[i + 1] + driftY) % 1f + 1f) % 1f * height
            val alpha = ((35 + starData[i + 3] * 110) * view.stars).toInt()
            paint.color = Color.argb(alpha, 220 + (depth * 30).toInt(), 225 + (depth * 25).toInt(), 248)
            val radius = (unit * (.0006f + depth * .001f)).coerceAtLeast(.55f)
            val trail = unit * (.003f + depth * .014f) * view.turn
            paint.strokeWidth = radius
            canvas.drawLine(x, y, x + trail, y - trail * .16f, paint)
            canvas.drawCircle(x, y, radius, paint)
        }
    }

    private fun globe(canvas: Canvas, body: CelestialBody, x: Float, y: Float, radius: Float, opacity: Float,
        cache: PlanetGlobes = images) {
        if (opacity <= 0 || radius < .5f || x + radius * 2.5f < 0) return
        if (body == CelestialBody.SATURN) rings(canvas, x, y, radius, opacity, false)
        val atmosphere = when (body) {
            CelestialBody.EARTH -> 0xff5eb2f4.toInt()
            CelestialBody.VENUS -> 0xffd5c4a0.toInt()
            CelestialBody.URANUS, CelestialBody.NEPTUNE -> 0xff7bb8cc.toInt()
            else -> 0
        }
        if (atmosphere != 0) {
            paint.color = Color.WHITE
            paint.alpha = (opacity * 255).toInt()
            paint.shader = RadialGradient(x, y, radius * 1.045f,
                intArrayOf(Color.TRANSPARENT, (atmosphere and 0xffffff) or 0x44000000, Color.TRANSPARENT),
                floatArrayOf(.90f, .963f, 1f), Shader.TileMode.CLAMP)
            canvas.drawCircle(x, y, radius * 1.045f, paint)
            paint.shader = null; paint.alpha = 255
        }
        cache.draw(canvas, body, x, y, radius, opacity)
        if (body == CelestialBody.SATURN) rings(canvas, x, y, radius, opacity, true)
        if (body == CelestialBody.URANUS) {
            val save = canvas.save(); canvas.rotate(72f, x, y)
            paint.style = Paint.Style.STROKE; paint.strokeWidth = radius * .004f
            paint.color = Color.argb((opacity * 75).toInt(), 155, 180, 176)
            ring.set(x - radius * 1.64f, y - radius * .18f, x + radius * 1.64f, y + radius * .18f)
            canvas.drawOval(ring, paint); paint.style = Paint.Style.FILL; canvas.restoreToCount(save)
        }
    }

    private fun rings(canvas: Canvas, x: Float, y: Float, radius: Float, opacity: Float, front: Boolean) {
        val save = canvas.save(); canvas.rotate(-25f, x, y)
        if (front) canvas.clipRect(x - radius * 2.4f, y, x + radius * 2.4f, y + radius)
        paint.style = Paint.Style.STROKE
        for (i in 0..100) {
            val r = 1.23f + i * .0102f
            // The Cassini division separates the brighter B ring from the outer A ring.
            if (r in 1.94f..2.015f) continue
            val strength = when {
                r < 1.5f -> .22f
                r < 1.94f -> .58f + sin(i * .8f) * .12f
                else -> .40f + sin(i * 1.2f) * .1f
            }
            paint.color = Color.argb((255 * opacity * strength).toInt(), 206, 193, 164)
            paint.strokeWidth = radius * .011f
            ring.set(x - radius * r, y - radius * r * .29f, x + radius * r, y + radius * r * .29f)
            canvas.drawOval(ring, paint)
        }
        paint.style = Paint.Style.FILL; canvas.restoreToCount(save)
    }

    private fun asteroids(canvas: Canvas, width: Float, height: Float, progress: Float) {
        val alpha = (sin(progress * PI) * 65).toInt().coerceAtLeast(0)
        val random = Random(91)
        repeat(18) {
            val depth = random.nextFloat()
            val x = ((random.nextFloat() - progress * (.1f + depth * .3f) + 1f) % 1f) * width
            val y = random.nextFloat() * height + progress * depth * height * .04f
            val r = min(width, height) * (.001f + depth * .004f)
            paint.color = Color.argb(alpha, 149, 144, 137)
            canvas.drawCircle(x, y, r, paint)
        }
    }

    override fun release() { super.release(); images.close(); distantImages.close() }
    private fun lerp(a: Float, b: Float, amount: Float) = a + (b - a) * amount
    private fun mix(a: Int, b: Int, amount: Float) = Color.rgb(
        lerp(Color.red(a).toFloat(), Color.red(b).toFloat(), amount).toInt(),
        lerp(Color.green(a).toFloat(), Color.green(b).toFloat(), amount).toInt(),
        lerp(Color.blue(a).toFloat(), Color.blue(b).toFloat(), amount).toInt())
}
