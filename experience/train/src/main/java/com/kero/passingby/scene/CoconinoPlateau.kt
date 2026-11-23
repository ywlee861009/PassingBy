package com.kero.passingby.scene

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LightingColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/** Pixel terrain and vegetation for the Williams–South Rim railroad. All artwork is baked off-thread. */
internal class CoconinoPlateau {
    private enum class Habitat { FOREST, PRAIRIE, WASH, LIMESTONE }
    private val ridges = Array(2) { depth ->
        IntArray(W) { x ->
            val t = 2 * PI * x / W
            val wave = if (depth == MID) 5 * sin(t + .4) + 3 * sin(3 * t) + 2 * cos(5 * t)
                else 3 * sin(2 * t) + 2 * cos(3 * t) + 1.5 * sin(6 * t)
            (16 + wave).roundToInt()
        }
    }
    private val foothills = Array(2) { bakeFoothills(it) }
    private val ground = Array(2) { depth -> Array(Habitat.entries.size) { bakeGround(depth, Habitat.entries[it]) } }
    private val pine = Array(7) { tree(true, 2411 + it) }
    private val juniper = Array(6) { tree(false, 2343 + it) }
    val bytes = foothills.sumOf { it.allocationByteCount.toLong() } + ground.sumOf { band -> band.sumOf { it.allocationByteCount.toLong() } } +
        pine.sumOf { it.allocationByteCount.toLong() } + juniper.sumOf { it.allocationByteCount.toLong() }
    private val paint = Paint()
    private val dst = RectF()
    private var previousMultiply = 0
    private var previousAdd = 0

    fun update(p: Palette) {
        val night = max(p.f[Palette.STARS], p.f[Palette.LIT] * .68f)
        val snow = p.f[Palette.SNOW]
        val haze = .11f + .75f * night
        val ambient = mix(p.c[Palette.HAZE], p.c[Palette.TREE], night)
        val multiply = mix(Color.WHITE, Color.BLACK, (haze + snow * .30f).coerceAtMost(.95f))
        val add = mix(mix(Color.BLACK, ambient, haze), p.c[Palette.SNOW_WHITE], snow * .32f)
        if (multiply != previousMultiply || add != previousAdd) {
            paint.colorFilter = LightingColorFilter(multiply, add)
            previousMultiply = multiply
            previousAdd = add
        }
    }

    fun groundY(layout: SceneLayout, depth: Int, wx: Double): Float {
        val x = Math.floorMod(floor(wx).toInt(), W)
        return layout.yAt(if (depth == MID) .90f else 1.02f) -
            (H - ridges[depth][x]) * heightScale(depth) * layout.u
    }

    fun draw(canvas: Canvas, layout: SceneLayout, state: SceneState, depth: Int) {
        val u = layout.u
        val speed = state.theme.layers[if (depth == MID) Scenery.MID else Scenery.NEAR].speed
        val off = state.distanceU * speed
        val screenU = layout.w / u
        val bottom = layout.yAt(if (depth == MID) .90f else 1.02f)
        val top = bottom - H * heightScale(depth) * u
        val z = state.zones
        val prairie = z.prairie(state.distanceU)
        val wash = z.dryWash(state.distanceU)
        val canyon = z.limestoneCanyon(state.distanceU)
        val first = floor(off / W).toInt()
        val last = floor((off + screenU) / W).toInt()
        for (cell in first..last) {
            val x = ((cell * W.toDouble() - off) * u).roundToInt().toFloat()
            val right = (((cell + 1) * W.toDouble() - off) * u).roundToInt().toFloat()
            dst.set(x, top, right, bottom)
            // Opaque interiors need one bitmap; only ecological transitions add a second ground pass.
            if (wash < 1f && canyon < 1f) {
                if (prairie < 1f) blit(canvas, ground[depth][Habitat.FOREST.ordinal], 1f)
                if (prairie > 0f) blit(canvas, ground[depth][Habitat.PRAIRIE.ordinal], prairie)
            }
            if (wash > 0f) blit(canvas, ground[depth][Habitat.WASH.ordinal], wash)
            if (canyon > 0f) blit(canvas, ground[depth][Habitat.LIMESTONE.ordinal], canyon)
        }
        val spacing = if (depth == MID) 11.0 else 27.0
        val seed = if (depth == MID) 703 else 765
        val k0 = floor((off - 50) / spacing).toInt()
        val k1 = floor((off + screenU + 50) / spacing).toInt()
        for (k in k0..k1) {
            val wx = (k + hash01(k, seed)) * spacing
            val worldX = (wx - screenU * .5) / speed
            val grass = z.prairie(worldX)
            val sand = z.dryWash(worldX)
            val rock = z.limestoneCanyon(worldX)
            val grove = valueNoise(k / 13.0, seed + 2).toFloat()
            val coverage = (.28f + .69f * grove) * (1f - .83f * grass) * (1f - .94f * sand) * (1f - .69f * rock)
            if (hash01(k, seed + 3) > coverage) continue
            // Keep the depot grounds open in the foreground; the woodland remains behind the buildings.
            if (depth == NEAR && state.urban.at(worldX) > .55f) continue
            val isPine = hash01(k, seed + 4) > .12 + .86 * grass + .18 * rock
            val variants = if (isPine) pine else juniper
            val sprite = variants[(hash01(k, seed + 5) * variants.size).toInt()]
            val size = hash01(k, seed + 6).toFloat()
            val height = when {
                depth == MID && isPine -> 16 + size * 25
                depth == MID -> 7 + size * 12
                isPine -> 42 + size * 43
                else -> 16 + size * 20
            } * u
            val root = groundY(layout, depth, wx) + (2f + size * 4f) * u
            val cx = ((wx - off) * u).toFloat()
            val half = height * sprite.width / sprite.height * .5f
            dst.set(cx - half, root - height, cx + half, root)
            blit(canvas, sprite, 1f)
        }
    }

    private fun heightScale(depth: Int) = if (depth == MID) 1.08f else .79f

    private fun blit(canvas: Canvas, bitmap: Bitmap, alpha: Float) {
        paint.alpha = (alpha * 255).roundToInt()
        canvas.drawBitmap(bitmap, null, dst, paint)
    }

    fun drawFoothills(canvas: Canvas, layout: SceneLayout, off: Double, layer: Int) {
        val bitmap = foothills[layer]
        val tileW = bitmap.width.toDouble()
        val u = layout.u
        val bottom = layout.yAt(if (layer == Scenery.FAR_A) .76f else .84f)
        val top = bottom - bitmap.height * u
        var tile = floor(off / tileW).toInt()
        while ((tile * tileW - off) * u < layout.w) {
            val left = ((tile * tileW - off) * u).roundToInt().toFloat()
            val right = (((tile + 1) * tileW - off) * u).roundToInt().toFloat()
            dst.set(left, top, right, bottom)
            blit(canvas, bitmap, 1f)
            tile++
        }
    }

    /** Wrap the lattice itself so slopes, arroyos and material patches continue across bitmap edges. */
    private fun texture(x: Double, y: Double, cells: Int, seed: Int): Float {
        val sx = x * cells
        val ix = floor(sx).toInt()
        val iy = floor(y).toInt()
        val fx = sx - ix
        val fy = y - iy
        val u = fx * fx * (3 - 2 * fx)
        val v = fy * fy * (3 - 2 * fy)
        val a = Math.floorMod(ix, cells)
        val b = (a + 1) % cells
        val row = iy * cells
        val lo = hash01(row + a, seed) * (1 - u) + hash01(row + b, seed) * u
        val hi = hash01(row + cells + a, seed) * (1 - u) + hash01(row + cells + b, seed) * u
        return (lo + (hi - lo) * v).toFloat()
    }

    /** Overlapping foothill spurs. Fluting follows the slope; scattered timber darkens shaded draws. */
    private fun bakeFoothills(layer: Int): Bitmap {
        val far = layer == Scenery.FAR_A
        val width = 1152
        val height = 126
        val seed = if (far) 760 else 820
        val faces = Array(3) { i ->
            IntArray(width) { x ->
                val f = x.toDouble() / width
                val broad = texture(f, i + .19, 5, seed)
                val knobs = texture(f, i + .75, 14, seed + 1)
                val outcrops = texture(f, i + .36, 39, seed + 2)
                (2 + i * 19 + broad * 33 + knobs * 10 + outcrops * 3).roundToInt()
            }
        }
        val shade = if (far) rgb(0x929EAD) else rgb(0x849187)
        val light = if (far) rgb(0xBCC4C1) else rgb(0xB5B69D)
        val timber = if (far) rgb(0x899AA4) else rgb(0x788779)
        val pixels = IntArray(width * height)
        for (x in 0 until width) {
            val f = x.toDouble() / width
            for (i in 0..2) {
                val crest = faces[i][x]
                val next = if (i == 2) height else faces[i + 1][x].coerceAtLeast(crest)
                val tilt = (faces[i][(x + 12) % width] - faces[i][Math.floorMod(x - 12, width)]) / 19f
                for (y in crest until next) {
                    val dz = y - crest
                    val slope = f - dz * .00078
                    val ravine = texture(slope, i + dz * .018, 30, seed + 5)
                    val facet = texture(slope, i + dz * .015, 9, seed + 7)
                    val sun = (.40f + tilt * .23f + .34f * facet + .18f * ravine - .10f * i).coerceIn(0f, 1f)
                    var color = mix(shade, light, sun)
                    val grove = texture(slope, y * .105, 68, seed + 8)
                    if (grove + ravine * .42f > .93f) color = mix(color, timber, if (far) .17f else .32f)
                    pixels[y * width + x] = color
                }
            }
        }
        val image = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        image.setPixels(pixels, 0, width, 0, 0, width, height)
        val c = Canvas(image)
        val ink = Paint()
        val r = SeqRandom(seed)
        for (i in 0..2) {
            ink.color = if (far) mix(timber, light, .35f) else mix(timber, light, i * .07f)
            for (x in 1 until width - 1 step 2) {
                val grove = texture(x.toDouble() / width, i + .6, 21, seed + 11)
                if (grove < .47f || r.next() > grove * .83f) continue
                val root = faces[i][x].toFloat()
                val treeH = (if (far) 1f else 2f) + r.next() * (if (far) 1.5f else 3f)
                c.drawRect(x.toFloat(), root - treeH, x + 1f, root + 1f, ink)
                c.drawRect(x - 1f, root - treeH * .4f, x + 2f, root + 1f, ink)
            }
        }
        return image
    }

    private fun bakeGround(depth: Int, habitat: Habitat): Bitmap {
        val pixels = IntArray(W * H)
        val near = depth == NEAR
        val base = when (habitat) {
            Habitat.FOREST -> rgb(if (near) 0x97815A else 0x97946E)
            Habitat.PRAIRIE -> rgb(if (near) 0xBA995F else 0xB9A478)
            Habitat.WASH -> rgb(if (near) 0xB79A75 else 0xBBA887)
            Habitat.LIMESTONE -> rgb(if (near) 0xAD9572 else 0xAA9C81)
        }
        val sun = rgb(if (near) 0xE1C892 else 0xDCC7A1)
        val grass = rgb(if (near) 0x716C46 else 0x82855E)
        for (x in 0 until W) {
            val f = x.toDouble() / W
            val t = 2 * PI * f
            val crest = ridges[depth][x]
            val swale = (sin(2 * t + .6) * 3 + sin(5 * t) * 2).roundToInt()
            val thalweg = (if (near) 27 else 45) + (8 * sin(t + .3) + 5 * sin(3 * t) + 2 * sin(6 * t)).roundToInt()
            val channelWidth = (8 + 3 * cos(t) + 2 * cos(4 * t)).roundToInt()
            val shelfHeight = (11 + 6 * sin(t + .4) + 4 * sin(3 * t) + 2 * sin(9 * t)).roundToInt()
            for (y in crest until H) {
                val speck = hash01(y * W + x, 587)
                val slopeY = y - crest * .46 - swale
                val patch = texture(f + slopeY * .0009, slopeY / 13, 11, 307 + depth)
                val tuft = texture(f, slopeY / 4.6, 44, 113)
                val fold = texture(f - slopeY * .0013, slopeY / 32, 6, 421)
                var color = mix(base, sun, .08f + .28f * fold)
                color = mix(color, grass, (.08f + .34f * patch + .16f * tuft - .19f * fold).coerceIn(0f, 1f))
                if (tuft > .72f && patch > .56f) color = mix(color, grass, .14f)
                if (habitat == Habitat.WASH) {
                    val cross = abs(y - thalweg) + (tuft - .5f) * 3
                    val sand = mix(rgb(0xBDA485), sun, .20f + .46f * patch)
                    color = when {
                        cross < channelWidth - 2 -> mix(sand, grass, .12f * tuft)
                        cross < channelWidth + 1 -> mix(sand, rgb(0x8F755B), .42f + .22f * tuft)
                        cross < channelWidth + 4 -> mix(color, sand, .48f * patch)
                        else -> color
                    }
                }
                if (habitat == Habitat.LIMESTONE && y - crest < shelfHeight) {
                    // Short cream-colored ledges with broken bedding planes and shadowed talus.
                    val bedding = Math.floorMod(y + swale / 2 + (patch * 2).roundToInt(), 6)
                    color = when (bedding) {
                        0 -> rgb(0x8F806E)
                        1 -> rgb(0xD9CAAD)
                        2 -> rgb(0xC6B69A)
                        else -> mix(rgb(0xB9AA91), base, .24f * patch)
                    }
                    if (speck > .92) color = mix(color, grass, .24f)
                }
                if (speck > .988 && tuft > .45f) color = mix(color, sun, .22f)
                pixels[y * W + x] = color
            }
        }
        val bitmap = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(pixels, 0, W, 0, 0, W, H)
        val canvas = Canvas(bitmap)
        val ink = Paint()
        val r = SeqRandom(4109 + habitat.ordinal * 93 + depth)
        if (depth == MID && habitat != Habitat.WASH) {
            // A second, much smaller stand on the far side of the meadow gives the visible trees depth.
            for (x in 2 until W - 2 step 3) {
                val crest = ridges[depth][x]
                val grove = .5 + .5 * sin(4 * PI * x / W + .3)
                val chance = if (habitat == Habitat.FOREST) .40 + .56 * grove else .07 + .24 * grove
                if (r.next() > chance) continue
                val height = if (habitat == Habitat.FOREST) 2f + r.next() * 6f else 1f + r.next() * 3f
                ink.color = if (habitat == Habitat.FOREST) rgb(0x737E6B) else rgb(0x899077)
                canvas.drawRect(x.toFloat(), crest - height, x + 1f, crest + 1f, ink)
                canvas.drawRect(x - 1f, crest - height * .62f, x + 2f, crest + 1f, ink)
                canvas.drawRect(x - 2f, crest - height * .30f, x + 3f, crest + 1f, ink)
            }
        }
        repeat(if (near) 350 else 440) {
            val x = (r.next() * W).toInt().coerceAtMost(W - 1)
            val y = (ridges[depth][x] + r.next() * (H - ridges[depth][x])).toInt().coerceAtMost(H - 1)
            val clump = texture(x.toDouble() / W, y / 12.0, 25, 944)
            if (clump < .34f) return@repeat
            val material = pixels[y * W + x]
            val stone = (habitat == Habitat.WASH || habitat == Habitat.LIMESTONE) && r.next() < .68f
            val len = if (near) 2f + r.next() * 4 else 1f + r.next() * 3
            ink.color = mix(material, if (stone) rgb(0x716953) else grass, .25f + r.next() * .32f)
            canvas.drawRect(x.toFloat(), y.toFloat(), x + len, y + 1f, ink)
            if (stone || habitat == Habitat.FOREST) {
                ink.color = mix(material, sun, .32f)
                canvas.drawRect(x + 1f, y - 1f, x + len, y.toFloat(), ink)
            } else {
                canvas.drawLine(x + 1f, y.toFloat(), x.toFloat(), y - len * .65f, ink)
                ink.color = mix(material, sun, .47f)
                canvas.drawLine(x + 1f, y.toFloat(), x + 3f, y - len, ink)
            }
        }
        return bitmap
    }

    private fun tree(tall: Boolean, seed: Int): Bitmap {
        val w = if (tall) 54 else 60
        val h = if (tall) 97 else 48
        val image = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(image)
        val ink = Paint()
        val r = SeqRandom(seed)
        val cx = w * .5f
        val crown = cx + (r.next() - .5f) * 4
        val bark = rgb(if (tall) 0x885A3B else 0x81735D)
        val shade = rgb(if (tall) 0x354E46 else 0x57644B)
        val needle = rgb(if (tall) 0x4C6653 else 0x728063)
        val glint = rgb(if (tall) 0x71856A else 0x939D75)
        ink.color = bark
        val trunk = Path().apply {
            moveTo(cx - 2.7f, h.toFloat())
            lineTo(crown - .8f, if (tall) 8f else 14f)
            lineTo(crown + .7f, if (tall) 8f else 14f)
            lineTo(cx + 2.3f, h.toFloat())
            close()
        }
        canvas.drawPath(trunk, ink)
        ink.color = rgb(if (tall) 0xAA7951 else 0xA19274)
        canvas.drawLine(cx + 1f, h - 1f, crown + .7f, h * .53f, ink)
        if (tall) {
            // Mature ponderosas carry separated, asymmetric boughs above an exposed copper-brown bole.
            val tiers = 10
            for (j in tiers downTo 0) {
                val f = j.toFloat() / tiers
                val y = 5 + f * (59 + 5 * r.next())
                for (side in -1..1 step 2) {
                    val reach = 2 + f * (11 + 10 * r.next())
                    val tipX = crown + side * reach
                    val tipY = y + 1f + 4f * r.next()
                    ink.color = bark
                    canvas.drawLine(crown, y + 5f, tipX, tipY, ink)
                    for (b in 1..4) {
                        val along = b / 4f
                        val x = crown + side * reach * along
                        val radius = 2f + 3.5f * f * (1 - .45f * along)
                        val cy = tipY - (1 - along) * 3f - r.next() * 3f
                        ink.color = shade
                        canvas.drawOval(x - radius, cy - radius, x + radius, cy + radius, ink)
                        ink.color = if (side > 0 && b > 2) glint else needle
                        canvas.drawOval(x - radius * .6f, cy - radius - 1f, x + radius, cy + radius * .27f, ink)
                    }
                }
            }
        } else {
            // Piñon-juniper woodland has low irregular crowns on multiple bent stems.
            for (j in 0 until 13) {
                val x = 7 + r.next() * (w - 14)
                val y = 9 + abs(x - cx) * .46f + r.next() * 8
                ink.color = bark
                ink.strokeWidth = 2f
                canvas.drawLine(cx, h - 4f, x, y, ink)
                val radius = 5f + r.next() * 5
                ink.color = shade
                canvas.drawOval(x - radius, y - radius, x + radius, y + radius, ink)
                ink.color = needle
                canvas.drawOval(x - radius * .85f, y - radius, x + radius, y + radius * .42f, ink)
                ink.color = glint
                canvas.drawRect(x + 1, y - radius + 1, x + radius * .63f, y - radius + 2, ink)
            }
        }
        return image
    }

    companion object {
        const val MID = 0
        const val NEAR = 1
        private const val W = 768
        private const val H = 83
        private fun rgb(hex: Int) = hex or Color.BLACK
    }
}
