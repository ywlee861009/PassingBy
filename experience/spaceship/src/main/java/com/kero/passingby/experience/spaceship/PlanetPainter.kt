package com.kero.passingby.experience.spaceship

import android.graphics.*
import kotlin.math.*
import kotlin.random.Random

/** Original vector-like illustrations, not photographic maps. All coordinates are sphere radii. */
internal class PlanetPainter {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val oval = RectF()
    private val disc = Path().apply { addCircle(0f, 0f, 1f, Path.Direction.CW) }
    private var centerLongitude = 22.0
    private var centerLatitude = 14.0

    fun draw(canvas: Canvas, body: CelestialBody, x: Float, y: Float, radius: Float) {
        val save = canvas.save()
        canvas.translate(x, y); canvas.scale(radius, radius); canvas.clipPath(disc)
        paint.color = Color.WHITE
        paint.shader = RadialGradient(-.4f, -.45f, 1.8f, body.color, body.shade, Shader.TileMode.CLAMP)
        canvas.drawCircle(0f, 0f, 1f, paint); paint.shader = null
        when (body) {
            CelestialBody.EARTH -> earth(canvas)
            CelestialBody.MOON, CelestialBody.MERCURY -> cratered(canvas, body)
            CelestialBody.VENUS -> venus(canvas)
            CelestialBody.MARS -> mars(canvas)
            CelestialBody.JUPITER -> gasGiant(canvas, false)
            CelestialBody.SATURN -> gasGiant(canvas, true)
            CelestialBody.URANUS -> iceGiant(canvas, false)
            CelestialBody.NEPTUNE -> iceGiant(canvas, true)
            CelestialBody.PLUTO -> pluto(canvas)
            else -> exoplanet(canvas, body)
        }
        // Soft hemisphere lighting preserves the hand-drawn palette and the round volume.
        paint.color = Color.WHITE
        paint.shader = RadialGradient(-.38f, -.4f, 1.65f,
            intArrayOf(0x18fff5dc, Color.TRANSPARENT, 0x1609192d, 0xb0040b19.toInt()),
            floatArrayOf(0f, .44f, .73f, 1f), Shader.TileMode.CLAMP)
        canvas.drawCircle(0f, 0f, 1f, paint)
        paint.shader = RadialGradient(0f, 0f, 1f,
            intArrayOf(Color.TRANSPARENT, 0x00040d1a, 0x53040d1a), floatArrayOf(0f, .83f, 1f), Shader.TileMode.CLAMP)
        canvas.drawCircle(0f, 0f, 1f, paint); paint.shader = null
        canvas.restoreToCount(save)
    }

    private fun earth(c: Canvas) {
        centerLongitude = 22.0; centerLatitude = 14.0
        paint.color = Color.WHITE
        paint.shader = RadialGradient(-.28f, -.3f, 1.55f,
            intArrayOf(0xff397fb0.toInt(), 0xff1b527e.toInt(), 0xff102b50.toInt()), null, Shader.TileMode.CLAMP)
        c.drawCircle(0f, 0f, 1f, paint); paint.shader = null
        val land = Path()
        for (outline in EarthContours.continents) land.addPath(geographic(outline))
        paint.color = 0xff6e8667.toInt(); c.drawPath(land, paint)
        // Coastal shelf follows the same geographic outline rather than arbitrary green blobs.
        paint.style = Paint.Style.STROKE; paint.strokeWidth = .009f; paint.color = 0x4852a29c
        c.drawPath(land, paint); paint.style = Paint.Style.FILL
        val save = c.save(); c.clipPath(land)
        // Sahara, Arabia, Eurasian steppe and southern Africa retain their real locations.
        geoWash(c, "-17,30 -7,36 10,36 25,32 35,31 42,17 28,13 15,14 2,17 -12,19", 0xffcbbb8c.toInt())
        geoWash(c, "35,30 48,30 58,23 51,13 42,13", 0xffcfba87.toInt())
        geoWash(c, "45,43 58,50 78,48 99,43 110,38 92,30 75,32 60,35", 0xffb8ae89.toInt())
        geoWash(c, "14,-18 29,-20 30,-30 18,-32 11,-25", 0xffc4ae84.toInt())
        geoWash(c, "-16,10 10,12 33,8 31,-7 18,-15 8,-6 -8,4", 0xff3a7052.toInt())
        geoWash(c, "-76,5 -50,4 -42,-8 -54,-22 -71,-15", 0xff426c58.toInt())
        geoWash(c, "-10,48 15,51 41,57 67,60 92,61 120,63 140,60 160,64 160,78 80,79 25,71 -10,60", 0xff8ba28a.toInt())
        geoFill(c, "-180,72 180,72 180,90 -180,90", 0xffcfddd7.toInt())
        val random = Random(204)
        repeat(950) {
            val p = project(random.nextDouble(-90.0, 155.0), random.nextDouble(-65.0, 78.0))
            if (p.z > 0) {
                val r = random.nextFloat() * .008f + .0015f
                paint.color = if (it % 3 == 0) 0x2574a179 else 0x196a674e
                oval.set(p.x - r * 1.8f, p.y - r * .55f, p.x + r * 1.8f, p.y + r * .55f)
                c.drawOval(oval, paint)
            }
        }
        // Fine, non-repeating terrain brushwork adds relief without importing raster textures.
        for (lat in -58..77 step 2) for (lon in -90..145 step 2) {
            val p = project(lon.toDouble(), lat.toDouble())
            if (p.z <= 0) continue
            val grain = sin(lon * 1.34 + lat * 2.37) * sin(lon * .51 - lat * 1.73)
            paint.color = if (grain > 0) Color.argb((grain * 33).toInt(), 222, 209, 164)
                else Color.argb((-grain * 38).toInt(), 46, 78, 53)
            val w = (.013 * max(.1, p.z)).toFloat()
            path.rewind(); path.moveTo(p.x - w, p.y)
            path.cubicTo(p.x - w * .4f, p.y - .008f, p.x + w, p.y - .006f, p.x + w, p.y + .004f)
            path.cubicTo(p.x + w * .2f, p.y + .01f, p.x - w, p.y + .007f, p.x - w, p.y)
            c.drawPath(path, paint)
        }
        mountainChain(c, "-7,31 2,33 11,33", 0xffddccaa.toInt(), 12)
        mountainChain(c, "6,45 10,47 15,46", 0xffdddcd0.toInt(), 13)
        mountainChain(c, "68,34 76,34 84,30 91,29 99,27", 0xffe0ddd1.toInt(), 35)
        mountainChain(c, "-71,-12 -70,-24 -71,-36 -73,-48", 0xffd3c6ad.toInt(), 24)
        mountainChain(c, "29,4 35,-1 38,-8", 0xffa6a28b.toInt(), 14)
        c.restoreToCount(save)
        // Closed inland water shapes: Mediterranean, Black Sea, Caspian, Great Rift lakes.
        geoFill(c, "-5,36 0,40 9,44 13,43 16,40 20,40 23,39 27,41 30,37 34,36 33,31 22,32 10,34 1,35", 0xff326c8c.toInt())
        geoFill(c, "27,41 30,45 36,46 41,43 40,41 33,41", 0xff3a7390.toInt())
        geoFill(c, "47,47 51,47 54,41 53,37 49,37 47,42", 0xff497b92.toInt())
        geoFill(c, "31,-1 34,-1 34,-3 32,-3", 0xff447e92.toInt())
        // Greenland / Antarctic snow, drawn on the globe rather than screen-space ovals.
        geoFill(c, "-53,60 -43,60 -25,69 -18,78 -28,83 -48,83 -62,76 -60,67", 0xffdee7df.toInt())
        geoFill(c, "-180,-72 -130,-74 -80,-67 -55,-64 -45,-74 0,-70 45,-69 90,-68 135,-70 180,-72 180,-90 -180,-90", 0xffd7e3df.toInt())
        cloudSystem(c, -39.0, 38.0, 19.0, .8, 21)
        cloudSystem(c, 64.0, -32.0, 22.0, -.9, 25)
        cloudSystem(c, -10.0, -43.0, 17.0, -.7, 16)
        // Broken tropical convergence zone; it does not hide the continent silhouettes.
        val clouds = Random(49)
        repeat(100) {
            val lon = clouds.nextDouble(-85.0, 132.0)
            val lat = 3.0 + sin(lon * .075) * 4 + clouds.nextDouble(-4.0, 4.0)
            cloudPuff(c, lon, lat, clouds.nextDouble(1.0, 3.2), .6, 82)
        }
        paint.style = Paint.Style.STROKE; paint.strokeWidth = .012f; paint.color = 0x657abde4
        c.drawCircle(0f, 0f, .989f, paint); paint.style = Paint.Style.FILL
    }

    private fun mountainChain(c: Canvas, coordinates: String, color: Int, steps: Int) {
        val points = parse(coordinates)
        for (i in 0 until steps) {
            val t = i.toDouble() / (steps - 1) * (points.size - 1)
            val a = points[t.toInt().coerceAtMost(points.lastIndex - 1)]
            val b = points[(t.toInt() + 1).coerceAtMost(points.lastIndex)]
            val f = t - floor(t)
            val p = project(a.first + (b.first - a.first) * f, a.second + (b.second - a.second) * f)
            if (p.z <= 0) continue
            val size = .004f + (sin(i * 2.9).toFloat() + 1) * .003f
            paint.color = 0x40544d45; oval.set(p.x - size, p.y, p.x + size * 2, p.y + size)
            c.drawOval(oval, paint)
            paint.color = color
            path.rewind(); path.moveTo(p.x - size, p.y + size * .4f)
            path.lineTo(p.x, p.y - size * .6f); path.lineTo(p.x + size, p.y + size * .2f); path.close()
            c.drawPath(path, paint)
        }
    }

    private fun cloudSystem(c: Canvas, lon: Double, lat: Double, radius: Double, spin: Double, count: Int) {
        for (arm in 0..1) for (i in 0 until count * 4) {
            val t = i.toDouble() / (count * 4)
            val angle = t * 5.5 * spin + arm * PI + sin(t * 27 + arm) * .07
            val spread = radius * (.12 + t * .88)
            for (lane in -2..2) {
                val drift = lane * .32 + sin(i * .61 + lane * 1.7) * .27
                val opacity = ((98 - t * 44) * (1 - abs(lane) * .22) * (.65 + .35 * sin(i * .35 + lane).pow(2))).toInt()
                cloudPuff(c, lon + cos(angle) * (spread + drift * 2.1), lat + sin(angle) * spread * .52 + drift,
                    (.8 + t * 1.5) * (1 + .35 * sin(i * 2.7 + lane)), .64, opacity)
            }
        }
    }

    private fun cloudPuff(c: Canvas, lon: Double, lat: Double, radius: Double, aspect: Double, alpha: Int) {
        val p = project(lon, lat)
        if (p.z <= 0) return
        val w = (radius * PI / 180 * max(.15, p.z)).toFloat()
        val h = (radius * PI / 180 * aspect).toFloat()
        val save = c.save(); c.translate(p.x, p.y); c.scale(w, h)
        paint.color = Color.WHITE
        paint.shader = RadialGradient(-.12f, -.15f, 1f,
            intArrayOf(Color.argb(alpha, 246, 248, 239), Color.argb(alpha * 2 / 3, 234, 243, 240), Color.TRANSPARENT),
            floatArrayOf(0f, .45f, 1f), Shader.TileMode.CLAMP)
        c.drawCircle(0f, 0f, 1f, paint); paint.shader = null; c.restoreToCount(save)
    }

    private fun gasGiant(c: Canvas, saturn: Boolean) {
        val palette = if (saturn) intArrayOf(0xffd4bf8e.toInt(), 0xffc4aa79.toInt(), 0xffe4d3a9.toInt(), 0xffaf9b79.toInt())
        else intArrayOf(0xffd8c8ac.toInt(), 0xff9f725c.toInt(), 0xffebe0c9.toInt(), 0xffb18b70.toInt(), 0xff796858.toInt())
        paint.color = palette[0]; c.drawCircle(0f, 0f, 1f, paint)
        val random = Random(if (saturn) 62 else 59)
        // Belts are projected latitude strips with turbulent boundaries, never straight rectangles.
        val bands = if (saturn) 78 else 96
        for (i in 0 until bands) {
            val latitude = -90.0 + i * 180.0 / bands
            val width = 180.0 / bands * 1.16
            val broad = i / 5
            val color = if (saturn) palette[(broad + if (i % 7 == 0) 1 else 0) % palette.size]
                else when {
                    latitude > 62 || latitude < -62 -> 0xff9e9483.toInt()
                    latitude > 47 -> 0xffb2a28a.toInt()
                    latitude > 38 -> 0xffc8b89a.toInt()
                    latitude > 30 -> 0xffa28b73.toInt()
                    latitude > 22 -> 0xffe1d3b2.toInt()
                    latitude > 7 -> 0xffad8064.toInt()
                    latitude > -7 -> 0xffe5d7b9.toInt()
                    latitude > -23 -> 0xffb18a70.toInt()
                    latitude > -30 -> 0xffe0cba4.toInt()
                    latitude > -39 -> 0xff978572.toInt()
                    latitude > -48 -> 0xffc9b99c.toInt()
                    else -> 0xffa99a83.toInt()
                }
            val alpha = if (saturn) 125 else 170
            paint.color = (color and 0xffffff) or (alpha shl 24)
            latitudeBand(c, latitude, width, if (saturn) .004f else .008f, i * .731f, !saturn)
        }
        repeat(if (saturn) 230 else 460) { i ->
            val yy = random.nextFloat() * 1.86f - .93f
            val xx = (random.nextFloat() * 2 - 1) * sqrt(1 - yy * yy)
            val width = (.015f + random.nextFloat() * .09f) * sqrt(1 - yy * yy)
            val h = if (saturn) .002f else .003f + random.nextFloat() * .005f
            paint.color = if (i % 3 == 0) 0x45725c4c else 0x65f4e5ca
            paint.style = Paint.Style.STROKE; paint.strokeWidth = if (saturn) .0015f else .0025f
            path.rewind(); path.moveTo(xx - width, yy)
            path.cubicTo(xx - width * .4f, yy - h * 2, xx + width * .3f, yy + h * 1.5f, xx + width, yy - h)
            c.drawPath(path, paint); paint.style = Paint.Style.FILL
        }
        if (saturn) {
            // Subtle northern hexagon, glimpsed in perspective near the pole.
            paint.color = 0x59728176; path.rewind()
            repeat(6) { i ->
                val a = PI / 3 * i
                val xx = cos(a).toFloat() * .18f; val yy = -.947f + sin(a).toFloat() * .025f
                if (i == 0) path.moveTo(xx, yy) else path.lineTo(xx, yy)
            }
            path.close(); c.drawPath(path, paint)
        } else {
            val jets = Random(771)
            repeat(30) { i ->
                val xx = jets.nextFloat() * 1.65f - .825f
                val yy = listOf(-.45f, -.17f, .09f, .49f)[i % 4] + jets.nextFloat() * .035f
                vortex(c, xx, yy, .018f + jets.nextFloat() * .024f, .009f,
                    0xffab9578.toInt(), 0xffdfcfac.toInt(), i)
            }
            // Great Red Spot: a broad oval south of the equator, nested turbulent rims.
            vortex(c, .34f, .31f, .23f, .115f, 0xffaf674c.toInt(), 0xffd9b78c.toInt(), 7)
            vortex(c, -.47f, .46f, .086f, .039f, 0xffb49173.toInt(), 0xfff0dfbd.toInt(), -11)
            vortex(c, .05f, -.22f, .072f, .031f, 0xffb8a58a.toInt(), 0xffede2c8.toInt(), 4)
            repeat(9) { i ->
                val xx = -.68f + i * .15f
                vortex(c, xx, .62f + sin(i * 1.8f) * .018f, .022f, .013f, 0xffd4c5a7.toInt(), 0xfff4e7c7.toInt(), i)
            }
        }
    }

    private fun latitudeBand(c: Canvas, latitude: Double, thickness: Double, waves: Float, phase: Float, storm: Boolean = false) {
        path.rewind()
        for (edge in 0..1) for (j in 0..120) {
            val fraction = if (edge == 0) j / 120.0 else 1.0 - j / 120.0
            val lon = -PI / 2 + fraction * PI
            val lat = (latitude + edge * thickness) * PI / 180
            val xx = (cos(lat) * sin(lon)).toFloat()
            var yy = (-sin(lat) - .085 * cos(lat) * cos(lon)).toFloat() +
                waves * (sin(xx * 19 + phase) + .55f * sin(xx * 43 - phase * 1.7f)) * cos(lon).toFloat()
            if (storm) {
                val dy = yy - .31f
                yy += (if (dy < 0) -.055f else .055f) * exp(-((xx - .34f) / .28f).pow(2) - (dy / .2f).pow(2))
            }
            if (edge == 0 && j == 0) path.moveTo(xx, yy) else path.lineTo(xx, yy)
        }
        path.close(); c.drawPath(path, paint)
    }

    private fun vortex(c: Canvas, x: Float, y: Float, w: Float, h: Float, core: Int, rim: Int, seed: Int) {
        paint.color = Color.WHITE
        paint.shader = RadialGradient(x - w * .22f, y - h * .3f, w,
            intArrayOf(core, rim, 0x006d5145), floatArrayOf(0f, .79f, 1f), Shader.TileMode.CLAMP)
        oval.set(x - w, y - h, x + w, y + h); c.drawOval(oval, paint); paint.shader = null
        paint.style = Paint.Style.STROKE
        for (i in 0..8) {
            val f = 1f - i / 10f
            paint.color = if (i % 3 == 0) (rim and 0xffffff) or 0x65000000 else (core and 0xffffff) or 0x42000000
            paint.strokeWidth = .0035f
            path.rewind()
            for (j in 0..110) {
                val a = j / 110.0 * 2 * PI
                val ripple = 1 + .035 * sin(a * 5 + i + seed)
                val xx = x + (cos(a) * w * f * ripple).toFloat()
                val yy = y + (sin(a) * h * f * ripple).toFloat()
                if (j == 0) path.moveTo(xx, yy) else path.lineTo(xx, yy)
            }
            c.drawPath(path, paint)
        }
        path.rewind()
        for (j in 0..160) {
            val t = j / 160f; val a = t * 3.4 * PI
            val xx = x + cos(a).toFloat() * w * t * .75f
            val yy = y + sin(a).toFloat() * h * t * .75f
            if (j == 0) path.moveTo(xx, yy) else path.lineTo(xx, yy)
        }
        paint.color = (core and 0xffffff) or 0x80000000.toInt(); c.drawPath(path, paint)
        paint.style = Paint.Style.FILL
    }

    private fun iceGiant(c: Canvas, neptune: Boolean) {
        val save = c.save(); if (!neptune) c.rotate(72f)
        paint.color = Color.WHITE
        paint.shader = LinearGradient(0f, -1f, 0f, 1f,
            if (neptune) intArrayOf(0xff648fae.toInt(), 0xff4e83a9.toInt(), 0xff47789e.toInt(), 0xff739aaf.toInt())
            else intArrayOf(0xffa4ccc9.toInt(), 0xffa9d1cf.toInt(), 0xff9ac6c7.toInt(), 0xff8fbabf.toInt()), null, Shader.TileMode.CLAMP)
        c.drawCircle(0f, 0f, 1f, paint); paint.shader = null
        repeat(48) { i ->
            paint.color = if (i % 3 == 0) (if (neptune) 0x18445973 else 0x0d577f83) else 0x10e0f1e8
            latitudeBand(c, -90.0 + i * 3.75, 2.0, .002f, i.toFloat())
        }
        if (neptune) {
            // Illustrative transient weather; not a claim that Voyager's old storm still exists.
            vortex(c, -.27f, .25f, .14f, .063f, 0xff375b7b.toInt(), 0xff658ea7.toInt(), 18)
            paint.color = 0x9bd7e6e6.toInt()
            oval.set(-.06f, .33f, .29f, .346f); c.drawOval(oval, paint)
            oval.set(.12f, -.3f, .47f, -.289f); c.drawOval(oval, paint)
        }
        c.restoreToCount(save)
    }

    private fun venus(c: Canvas) {
        paint.color = 0xffcbb794.toInt(); c.drawCircle(0f, 0f, 1f, paint)
        val random = Random(24)
        // The visible cloud deck has broad curved streaks; the rocky ground stays concealed.
        repeat(150) { i ->
            val y = -1.2f + i * .016f
            path.rewind()
            for (side in 0..1) for (j in 0..60) {
                val x = (if (side == 0) j / 30f - 1f else 1f - j / 30f)
                val yy = y + .22f * x * x + .035f * sin(x * 5 + i * .13f) + side * .018f
                if (side == 0 && j == 0) path.moveTo(x, yy) else path.lineTo(x, yy)
            }
            path.close()
            paint.color = if (i % 5 < 2) Color.argb(35 + random.nextInt(45), 242, 224, 186)
                else Color.argb(20 + random.nextInt(32), 143, 121, 89)
            c.drawPath(path, paint)
        }
    }

    private fun cratered(c: Canvas, body: CelestialBody) {
        val mercury = body == CelestialBody.MERCURY
        val random = Random(if (mercury) 73 else 11)
        // Moon's near-side maria form a recognisable cluster, distinct from Mercury's plains.
        if (!mercury) {
            patch(c, -.41f, -.42f, .28f, .22f, 0xff7d8182.toInt(), 6)
            patch(c, -.08f, -.33f, .19f, .23f, 0xff848788.toInt(), 13)
            patch(c, .33f, -.38f, .21f, .23f, 0xff777e81.toInt(), 9)
            patch(c, .57f, -.17f, .13f, .12f, 0xff888c8b.toInt(), 7)
            patch(c, -.35f, .07f, .35f, .26f, 0xff8a8b86.toInt(), 8)
            patch(c, .10f, .07f, .18f, .18f, 0xff818583.toInt(), 15)
        } else patch(c, -.29f, -.24f, .48f, .38f, 0xffa09787.toInt(), 12)
        repeat(330) { i ->
            val x = random.nextFloat() * 2 - 1; val y = random.nextFloat() * 2 - 1
            if (x * x + y * y > .96f) return@repeat
            val r = if (i < 35) .025f + random.nextFloat() * .05f else .004f + random.nextFloat() * .017f
            crater(c, x, y, r, if (mercury) 0xff8c8277.toInt() else 0xff9c9b92.toInt())
        }
        // Tycho / Hokusai-like ejecta rays, visibly radial rather than white speckles.
        val tx = if (mercury) .30f else .13f; val ty = if (mercury) -.22f else .61f
        paint.style = Paint.Style.STROKE
        repeat(24) { i ->
            val a = i * PI * 2 / 24 + .09 * sin(i * 3.4)
            val reach = .11f + random.nextFloat() * .29f
            paint.color = 0x47e3ded0; paint.strokeWidth = .001f + random.nextFloat() * .003f
            c.drawLine(tx, ty, tx + cos(a).toFloat() * reach, ty + sin(a).toFloat() * reach, paint)
        }
        paint.style = Paint.Style.FILL; crater(c, tx, ty, .031f, 0xffaaa79a.toInt())
    }

    private fun crater(c: Canvas, x: Float, y: Float, radius: Float, floor: Int) {
        val aspect = sqrt(max(.03f, 1 - x * x - y * y))
        val save = c.save(); c.translate(x, y); c.rotate(atan2(y, x) * 180f / PI.toFloat()); c.scale(aspect, 1f)
        paint.color = 0x50e2dcc7; c.drawCircle(0f, 0f, radius * 1.13f, paint)
        paint.color = Color.WHITE
        paint.shader = LinearGradient(-radius, -radius, radius, radius,
            intArrayOf(0xff615f59.toInt(), floor, 0xffc7c1af.toInt()), null, Shader.TileMode.CLAMP)
        c.drawCircle(0f, 0f, radius, paint); paint.shader = null
        paint.style = Paint.Style.STROKE; paint.strokeWidth = radius * .11f; paint.color = 0x70e7dfc9
        oval.set(-radius, -radius, radius, radius); c.drawArc(oval, 25f, 130f, false, paint)
        paint.style = Paint.Style.FILL; c.restoreToCount(save)
    }

    private fun mars(c: Canvas) {
        centerLongitude = -70.0; centerLatitude = 12.0
        paint.color = 0xffbe8466.toInt(); c.drawCircle(0f, 0f, 1f, paint)
        // Low-contrast albedo regions allow the relief to read as dusty rock rather than continents.
        patch(c, -.18f, .27f, .73f, .29f, 0x54836c58, 4)
        patch(c, .59f, -.04f, .36f, .28f, 0x60746657, 7)
        patch(c, -.49f, -.3f, .4f, .24f, 0x65c79874, 9)
        val random = Random(88)
        repeat(520) {
            val x = random.nextFloat() * 2 - 1; val y = random.nextFloat() * 2 - 1
            paint.color = if (it % 3 == 0) 0x1e795e4d else 0x1acfab83
            val r = .002f + random.nextFloat() * .017f
            oval.set(x - r * 2, y - r, x + r * 2, y + r); c.drawOval(oval, paint)
        }
        repeat(180) {
            val x = random.nextFloat() * 2 - 1; val y = random.nextFloat() * 2 - 1
            if (x * x + y * y < .94f) crater(c, x, y, .002f + random.nextFloat() * .013f, 0xffb17e60.toInt())
        }
        // Tharsis shields / Olympus Mons and the long equatorial Valles Marineris rift.
        for ((lon, lat) in listOf(-133.0 to 18.6, -113.0 to 11.0, -112.0 to 0.8, -104.0 to -8.4)) {
            val p = project(lon, lat)
            if (p.z > 0) {
                paint.color = Color.WHITE
                paint.shader = RadialGradient(p.x - .008f, p.y - .01f, .067f,
                    intArrayOf(0xffc7a387.toInt(), 0xff9c7058.toInt(), 0x009c7058), null, Shader.TileMode.CLAMP)
                c.drawCircle(p.x, p.y, .067f, paint); paint.shader = null
                crater(c, p.x, p.y, .008f, 0xff8a6651.toInt())
            }
        }
        val canyon = geographicLine("-95,-8 -85,-9 -76,-12 -68,-11 -59,-14 -50,-13")
        paint.style = Paint.Style.STROKE; paint.strokeCap = Paint.Cap.ROUND
        paint.color = 0x996b5146.toInt(); paint.strokeWidth = .018f; c.drawPath(canyon, paint)
        paint.color = 0xff9d7158.toInt(); paint.strokeWidth = .006f; c.drawPath(canyon, paint)
        for (i in 0..16) {
            val x = -.37f + i * .043f; val y = .26f + sin(i * .5f) * .03f
            path.rewind(); path.moveTo(x, y)
            path.cubicTo(x + .02f, y - .025f, x - .018f, y - .045f, x + .012f, y - .065f)
            paint.color = 0x50725949; paint.strokeWidth = .002f; c.drawPath(path, paint)
        }
        paint.style = Paint.Style.FILL
        polarCap(c, .89f, 0xffddd7c3.toInt(), 9)
        polarCap(c, -.94f, 0xffeee6d0.toInt(), 6)
    }

    private fun pluto(c: Canvas) {
        paint.color = 0xffaa9380.toInt(); c.drawCircle(0f, 0f, 1f, paint)
        patch(c, -.37f, .16f, .62f, .30f, 0xff77635a.toInt(), 6)
        patch(c, .53f, -.25f, .40f, .39f, 0xffc3b5a0.toInt(), 5)
        // Tombaugh Regio's unequal heart lobes; western Sputnik Planitia is smoother/brighter.
        path.rewind(); path.moveTo(-.06f, .42f)
        path.cubicTo(-.22f, .21f, -.58f, -.08f, -.42f, -.32f)
        path.cubicTo(-.30f, -.50f, -.07f, -.43f, .02f, -.24f)
        path.cubicTo(.19f, -.43f, .48f, -.35f, .49f, -.13f)
        path.cubicTo(.48f, .05f, .13f, .24f, -.06f, .42f); path.close()
        paint.color = 0xffd5cebc.toInt(); c.drawPath(path, paint)
        val save = c.save(); c.clipPath(path)
        val random = Random(66)
        repeat(160) {
            val x = random.nextFloat() - .5f; val y = random.nextFloat() - .45f
            paint.color = if (x < 0) 0x24f5eddb else 0x247a786e
            paint.style = Paint.Style.STROKE; paint.strokeWidth = .0015f
            oval.set(x - .025f, y - .016f, x + .025f, y + .016f); c.drawOval(oval, paint)
        }
        paint.style = Paint.Style.FILL; c.restoreToCount(save)
        polarCap(c, -.75f, 0xffbcb8aa.toInt(), 8)
    }

    private fun exoplanet(c: Canvas, body: CelestialBody) {
        // These surfaces are intentionally interpretive; the course notes explain the uncertainty.
        val random = Random(301 + body.ordinal)
        val icy = body in listOf(CelestialBody.TRAPPIST_F, CelestialBody.TRAPPIST_G, CelestialBody.LHS1140_B)
        val lava = body == CelestialBody.CANCRI_E
        repeat(60) { i ->
            val x = random.nextFloat() * 2 - 1; val y = random.nextFloat() * 2 - 1
            patch(c, x, y, .04f + random.nextFloat() * .21f, .03f + random.nextFloat() * .14f,
                if (icy) 0x70d3dcdb else (body.shade and 0xffffff) or 0x60000000, i)
        }
        if (icy) {
            paint.style = Paint.Style.STROKE; paint.strokeWidth = .002f; paint.color = 0x50677b88
            repeat(40) {
                val x = random.nextFloat() * 2 - 1; val y = random.nextFloat() * 2 - 1
                path.rewind(); path.moveTo(x, y); path.rLineTo(.07f, -.09f); path.rLineTo(-.02f, -.08f)
                c.drawPath(path, paint)
            }
            paint.style = Paint.Style.FILL
        } else if (lava) {
            repeat(38) {
                val x = random.nextFloat() * 2 - 1; val y = random.nextFloat() * 2 - 1
                path.rewind(); path.moveTo(x, y)
                repeat(5) { path.rLineTo(random.nextFloat() * .15f - .05f, random.nextFloat() * .16f - .08f) }
                paint.style = Paint.Style.STROKE; paint.strokeWidth = .012f; paint.color = 0x70f67f35
                c.drawPath(path, paint); paint.strokeWidth = .0025f; paint.color = 0xfff5bf6b.toInt(); c.drawPath(path, paint)
            }
            paint.style = Paint.Style.FILL
        } else repeat(55) {
            val x = random.nextFloat() * 2 - 1; val y = random.nextFloat() * 2 - 1
            if (x * x + y * y < .9f) crater(c, x, y, .005f + random.nextFloat() * .025f, body.shade)
        }
    }

    private fun polarCap(c: Canvas, latitudeY: Float, color: Int, seed: Int) {
        path.rewind(); path.moveTo(-1f, if (latitudeY < 0) -1.1f else 1.1f)
        for (i in 0..100) {
            val x = -1f + i * .02f
            val y = latitudeY + .015f * sin(x * 25 + seed) + .009f * sin(x * 57)
            path.lineTo(x, y)
        }
        path.lineTo(1f, if (latitudeY < 0) -1.1f else 1.1f); path.close()
        paint.color = color; c.drawPath(path, paint)
    }

    private fun patch(c: Canvas, x: Float, y: Float, w: Float, h: Float, color: Int, seed: Int) {
        path.rewind()
        for (i in 0..90) {
            val a = i * PI * 2 / 90
            val r = 1 + .12 * sin(a * 5 + seed) + .07 * sin(a * 11 - seed * .7)
            val xx = x + cos(a).toFloat() * w * r.toFloat()
            val yy = y + sin(a).toFloat() * h * r.toFloat()
            if (i == 0) path.moveTo(xx, yy) else path.lineTo(xx, yy)
        }
        path.close(); paint.color = color; c.drawPath(path, paint)
    }

    private data class Projection(val x: Float, val y: Float, val z: Double)
    private fun project(lon: Double, lat: Double): Projection {
        val a = (lon - centerLongitude) * PI / 180; val b = lat * PI / 180; val tilt = centerLatitude * PI / 180
        return Projection((cos(b) * sin(a)).toFloat(),
            (sin(tilt) * cos(b) * cos(a) - cos(tilt) * sin(b)).toFloat(),
            sin(tilt) * sin(b) + cos(tilt) * cos(b) * cos(a))
    }

    private fun parse(text: String) = text.trim().split(Regex("\\s+")).map {
        val pair = it.split(','); pair[0].toDouble() to pair[1].toDouble()
    }

    private fun geoFill(c: Canvas, text: String, color: Int) {
        paint.color = color; c.drawPath(geographic(text), paint)
    }

    private fun geoWash(c: Canvas, text: String, color: Int) {
        val points = parse(text).map { project(it.first, it.second) }.filter { it.z > 0 }
        if (points.isEmpty()) return
        val x = points.map { it.x.toDouble() }.average().toFloat()
        val y = points.map { it.y.toDouble() }.average().toFloat()
        val radius = points.maxOf { hypot(it.x - x, it.y - y) }.coerceAtLeast(.02f) * 1.15f
        paint.color = Color.WHITE
        paint.shader = RadialGradient(x, y, radius,
            intArrayOf(color, (color and 0xffffff) or 0xb0000000.toInt(), color and 0xffffff),
            floatArrayOf(0f, .45f, 1f), Shader.TileMode.CLAMP)
        c.drawPath(geographic(text), paint); paint.shader = null
    }

    /** Densify the contour, clip its back hemisphere and join hidden stretches along the limb. */
    private fun geographic(text: String): Path {
        val input = parse(text)
        val samples = mutableListOf<Projection>()
        for (i in input.indices) {
            val a = input[i]; val b = input[(i + 1) % input.size]
            val steps = ceil(max(abs(a.first - b.first), abs(a.second - b.second)) * 2).toInt().coerceAtLeast(1)
            repeat(steps) { j ->
                val f = j.toDouble() / steps
                val before = input[(i + input.size - 1) % input.size]
                val after = input[(i + 2) % input.size]
                fun smooth(p0: Double, p1: Double, p2: Double, p3: Double): Double {
                    if (abs(p1 - p2) > 90 || abs(p0 - p1) > 90 || abs(p2 - p3) > 90) return p1 + (p2 - p1) * f
                    return .5 * ((2 * p1) + (-p0 + p2) * f + (2 * p0 - 5 * p1 + 4 * p2 - p3) * f * f +
                        (-p0 + 3 * p1 - 3 * p2 + p3) * f * f * f)
                }
                val lon = smooth(before.first, a.first, b.first, after.first)
                val lat = smooth(before.second, a.second, b.second, after.second).coerceIn(-90.0, 90.0)
                samples += project(lon, lat)
            }
        }
        val result = Path(); val start = samples.indexOfFirst { it.z > 0 }
        if (start < 0) return result
        val first = samples[start]; result.moveTo(first.x, first.y)
        var exit: Projection? = null
        for (k in 1..samples.size) {
            val a = samples[(start + k - 1) % samples.size]; val b = samples[(start + k) % samples.size]
            if (a.z >= 0 && b.z >= 0) result.lineTo(b.x, b.y)
            else if ((a.z >= 0) != (b.z >= 0)) {
                val t = (a.z / (a.z - b.z)).toFloat()
                var xx = a.x + (b.x - a.x) * t; var yy = a.y + (b.y - a.y) * t
                val r = hypot(xx, yy); xx /= r; yy /= r
                val edge = Projection(xx, yy, 0.0)
                if (a.z >= 0) { result.lineTo(xx, yy); exit = edge }
                else {
                    exit?.let {
                        val angle = atan2(it.y.toDouble(), it.x.toDouble())
                        var sweep = atan2(yy.toDouble(), xx.toDouble()) - angle
                        while (sweep > PI) sweep -= 2 * PI
                        while (sweep < -PI) sweep += 2 * PI
                        val steps = ceil(abs(sweep) * 100).toInt().coerceAtLeast(1)
                        for (j in 1..steps) {
                            val theta = angle + sweep * j / steps
                            result.lineTo(cos(theta).toFloat(), sin(theta).toFloat())
                        }
                    }
                    result.lineTo(xx, yy); result.lineTo(b.x, b.y)
                }
            }
        }
        result.close(); return result
    }

    private fun geographicLine(text: String): Path {
        val result = Path(); var started = false
        for ((lon, lat) in parse(text)) {
            val p = project(lon, lat)
            if (p.z <= 0) { started = false; continue }
            if (!started) result.moveTo(p.x, p.y) else result.lineTo(p.x, p.y)
            started = true
        }
        return result
    }
}
