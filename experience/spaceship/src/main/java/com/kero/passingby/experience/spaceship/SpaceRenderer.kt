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
    private val ufo = SpaceUfo.View()
    private val landscape = EarthLaunchLandscape()
    private val starData = FloatArray(180 * 4).apply {
        val random = Random(108)
        for (i in indices step 4) {
            this[i] = random.nextFloat(); this[i + 1] = random.nextFloat()
            this[i + 2] = random.nextFloat(); this[i + 3] = random.nextFloat()
        }
    }
    private val ring = RectF()
    // Filtered gas emission is composited separately from the full-resolution shadow.
    private var gasSurface: Bitmap? = null
    private var gasCanvas: Canvas? = null
    private var foregroundGas: Bitmap? = null
    private var foregroundCanvas: Canvas? = null
    private val gasBounds = RectF()
    private val gasPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    override val roundness = .12f
    override val bitmapBytes: Long get() = images.bytes + distantImages.bytes + (gasSurface?.allocationByteCount ?: 0) + (foregroundGas?.allocationByteCount ?: 0)

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
            flyingSaucer(canvas, width, height)
        }
        // Very faint window reflections; they do not obscure surface details.
        paint.shader = null; paint.style = Paint.Style.FILL; paint.color = 0x04d3e8f7
        path.rewind(); path.moveTo(width * .1f, 0f); path.lineTo(width * .115f, 0f)
        path.lineTo(width * .38f, height); path.lineTo(width * .35f, height); path.close()
        canvas.drawPath(path, paint)
    }

    private fun flyingSaucer(canvas: Canvas, width: Float, height: Float) {
        SpaceUfo.sample(scene.progress.toDouble() * scene.durationSeconds, scene.durationSeconds,
            scene.course.stations.size, scene.direction, scene.eventSeed, scene.course.id, ufo)
        if (ufo.opacity <= 0f) return
        val r = ufo.radius * min(width, height)
        val x = ufo.x * width
        val y = ufo.y * height
        val saved = canvas.save()
        canvas.rotate(ufo.bank, x, y)
        paint.style = Paint.Style.FILL
        paint.shader = RadialGradient(x, y + r * .12f, r * 1.8f,
            intArrayOf(Color.argb((ufo.opacity * 55).toInt(), 111, 231, 216), Color.TRANSPARENT),
            null, Shader.TileMode.CLAMP)
        canvas.drawOval(x - r * 1.8f, y - r * .5f, x + r * 1.8f, y + r * .75f, paint)
        paint.shader = null
        paint.color = Color.argb((ufo.opacity * 255).toInt(), 45, 72, 86)
        canvas.drawOval(x - r * .53f, y - r * .68f, x + r * .53f, y + r * .08f, paint)
        paint.color = Color.argb((ufo.opacity * 220).toInt(), 143, 218, 226)
        canvas.drawOval(x - r * .41f, y - r * .59f, x + r * .34f, y - r * .06f, paint)
        paint.color = Color.argb((ufo.opacity * 180).toInt(), 218, 249, 248)
        canvas.drawOval(x - r * .28f, y - r * .51f, x - r * .10f, y - r * .25f, paint)
        paint.color = Color.argb((ufo.opacity * 255).toInt(), 62, 85, 101)
        canvas.drawOval(x - r * 1.25f, y - r * .17f, x + r * 1.25f, y + r * .43f, paint)
        paint.color = Color.argb((ufo.opacity * 255).toInt(), 178, 199, 207)
        canvas.drawOval(x - r * 1.25f, y - r * .23f, x + r * 1.25f, y + r * .20f, paint)
        paint.color = Color.argb((ufo.opacity * 200).toInt(), 223, 237, 236)
        canvas.drawOval(x - r * 1.05f, y - r * .16f, x + r * .85f, y - r * .04f, paint)
        // Steady teal portholes instead of flashing lights or a full-screen beam.
        paint.color = Color.argb((ufo.opacity * 235).toInt(), 121, 231, 203)
        for (i in 0..4) {
            val dx = (i - 2) * r * .35f
            canvas.drawCircle(x + dx, y + r * (.23f - abs(i - 2) * .025f), r * .055f, paint)
        }
        canvas.restoreToCount(saved)
    }

    private fun passingWorlds(canvas: Canvas, width: Float, height: Float, position: Float) {
        val course = scene.course as SpaceCourse
        val leg = position.toInt().coerceIn(course.bodies.indices)
        // Cruise scenery adds no destinations or countdowns to the course.
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
        if (body == CelestialBody.BLACK_HOLE) {
            blackHole(canvas, x, y, radius, opacity)
            return
        }
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

    /** Layered lensing approximation: direct disk, rear image, shadow, photon ring, foreground. */
    private fun blackHole(canvas: Canvas, x: Float, y: Float, radius: Float, opacity: Float) {
        val surface = gasSurface ?: Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888).also {
            gasSurface = it
            gasCanvas = Canvas(it)
        }
        val extent = radius * .72f * 2.6f
        gasBounds.set(x - extent, y - extent, x + extent, y + extent)
        surface.eraseColor(Color.TRANSPARENT)
        drawBlackHoleGas(gasCanvas!!, opacity, foreground = false)
        gasPaint.alpha = 255
        canvas.drawBitmap(surface, null, gasBounds, gasPaint)

        // Draw the silhouette and photon ring at the viewport resolution, between gas layers.
        val saved = canvas.save()
        canvas.translate(x, y)
        canvas.scale(radius * .72f, radius * .72f)
        paint.shader = null
        paint.strokeCap = Paint.Cap.BUTT
        paint.style = Paint.Style.FILL
        paint.color = Color.argb((opacity * 255).toInt(), 1, 2, 5)
        canvas.drawCircle(0f, 0f, .77f, paint)
        paint.style = Paint.Style.STROKE
        for (i in 5 downTo 0) {
            paint.strokeWidth = .006f + i * .006f
            paint.color = Color.argb((opacity * (if (i == 0) 180 else 12)).toInt(), 255, 225, 173)
            canvas.drawCircle(0f, 0f, .785f, paint)
        }
        canvas.restoreToCount(saved)
        val front = foregroundGas ?: Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888).also {
            foregroundGas = it
            foregroundCanvas = Canvas(it)
        }
        front.eraseColor(Color.TRANSPARENT)
        drawBlackHoleGas(foregroundCanvas!!, opacity, foreground = true)
        gasPaint.alpha = 255
        canvas.drawBitmap(front, null, gasBounds, gasPaint)
    }

    private fun drawBlackHoleGas(canvas: Canvas, opacity: Float, foreground: Boolean) {
        val saved = canvas.save()
        canvas.translate(256f, 256f)
        canvas.scale(512f / 5.2f, 512f / 5.2f)
        canvas.rotate(-12f)
        val phase = (scene.animationSeconds * .65).toFloat()
        if (!foreground) {
            paint.style = Paint.Style.FILL
            paint.color = Color.WHITE
            paint.shader = RadialGradient(0f, 0f, 2.5f,
                intArrayOf(Color.TRANSPARENT, Color.argb((opacity * 65).toInt(), 239, 151, 65), Color.TRANSPARENT),
                floatArrayOf(0f, .42f, 1f), Shader.TileMode.CLAMP)
            canvas.drawCircle(0f, 0f, 2.5f, paint)
            paint.shader = null
            paint.style = Paint.Style.STROKE
            // The rear disk's apparent image bends up and around the central shadow.
            // A smaller, dimmer secondary image curves beneath it.
            for (i in 0..56) {
                val t = i / 56f
                val r = .81f + t * .48f
                paint.strokeWidth = .048f
                paint.strokeCap = Paint.Cap.ROUND
                for (segment in 0 until 64) {
                    val angle = segment * 2.8125f
                    val theta = angle * PI.toFloat() / 180f
                    val flow = phase / (.65f + t * 1.7f)
                    val texture = .73f + .18f * sin(theta * 5f + t * 7f - flow * 2f) +
                        .09f * sin(theta * 11f - t * 13f - flow * 3f)
                    val fade = (1f - t).pow(.8f)
                    paint.color = Color.argb((opacity * fade * texture * 85).toInt(), 255,
                        (235 - t * 100).toInt(), (185 - t * 130).toInt())
                    ring.set(-r * 1.22f, -r, r * 1.22f, r * .68f)
                    canvas.drawArc(ring, 180f + angle, 3.6f, false, paint)
                    paint.color = Color.argb((opacity * fade * texture * 35).toInt(), 255, 190, 115)
                    ring.set(-r * 1.15f, -r * .65f, r * 1.15f, r * .86f)
                    canvas.drawArc(ring, angle, 3.6f, false, paint)
                }
            }
        } else {
            paint.style = Paint.Style.STROKE
            paint.strokeCap = Paint.Cap.ROUND
            // Draw the direct disk last so its near edge passes in front of the shadow.
            // Azimuthal brightness and drifting filaments suggest orbiting hot material.
            for (i in 0..64) {
                val t = i / 64f
                val r = .84f + t * 1.38f
                paint.strokeWidth = .07f
                ring.set(-r, -r * .13f, r, r * .13f)
                for (segment in 0 until 96) {
                    val angle = segment * 3.75f
                    val theta = angle * PI.toFloat() / 180f
                    val near = sin(theta) >= 0f
                    if (!near) continue
                    val hotSide = .65f + .35f * -cos(theta)
                    val flow = phase / (.55f + t * 2.2f)
                    val filament = .70f + .20f * sin(t * 8f + theta * 5f - flow * 2f) +
                        .10f * sin(t * 17f - theta * 11f + flow * 3f)
                    val fade = (1f - t).pow(.65f)
                    paint.color = Color.argb((opacity * 95 * hotSide * filament * fade).toInt(),
                        255, (225 - t * 125).toInt(), (160 - t * 125).toInt())
                    canvas.drawArc(ring, angle, 4.1f, false, paint)
                }
            }
        }
        paint.shader = null
        paint.strokeCap = Paint.Cap.BUTT
        paint.style = Paint.Style.FILL
        canvas.restoreToCount(saved)
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

    override fun release() { super.release(); images.close(); distantImages.close(); gasCanvas = null; gasSurface?.recycle(); gasSurface = null; foregroundCanvas = null; foregroundGas?.recycle(); foregroundGas = null }
    private fun lerp(a: Float, b: Float, amount: Float) = a + (b - a) * amount
    private fun mix(a: Int, b: Int, amount: Float) = Color.rgb(
        lerp(Color.red(a).toFloat(), Color.red(b).toFloat(), amount).toInt(),
        lerp(Color.green(a).toFloat(), Color.green(b).toFloat(), amount).toInt(),
        lerp(Color.blue(a).toFloat(), Color.blue(b).toFloat(), amount).toInt())
}
