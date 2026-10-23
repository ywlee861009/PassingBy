package com.kero.passingby.scene

import com.kero.passingby.experience.TimeOfDay

import kotlin.math.abs

/**
 * 세션 진행(0~1) → 하늘 팔레트. 25분이든 90분이든 한 세션 안에서 밤 끝 → 새벽 → 낮 → 노을 → 밤을 모두 본다(명세 7.4).
 * 처음과 끝이 모두 밤 쪽이라 다음 세션으로 이어질 때 하늘이 튀지 않는다.
 */
object SkyCycle {
    /** 진행 위치와 그때의 시간대. 사이는 부드럽게 섞는다. */
    private val AT = floatArrayOf(0f, .07f, .16f, .28f, .56f, .68f, .79f, .91f, 1f)
    private val KEYS = arrayOf(
        TimeOfDay.NIGHT, TimeOfDay.DAWN, TimeOfDay.DAWN, TimeOfDay.DAY,
        TimeOfDay.DAY, TimeOfDay.DUSK, TimeOfDay.DUSK, TimeOfDay.NIGHT, TimeOfDay.NIGHT,
    )
    /** 출발 전 정차 화면은 완전한 밤보다 조금 밝은 새벽 직전에서 시작한다. */
    private const val START = .03f

    /** [progress] 시점의 하늘을 [out]에 채우고 가장 가까운 시간대를 돌려준다. */
    fun paletteAt(progress: Float, out: Palette): TimeOfDay {
        val p = (START + (1 - START) * progress).coerceIn(0f, 1f)
        var i = 0
        while (i < AT.size - 2 && p >= AT[i + 1]) i++
        val a = KEYS[i]
        val b = KEYS[i + 1]
        val x = ((p - AT[i]) / (AT[i + 1] - AT[i])).coerceIn(0f, 1f)
        val e = x * x * (3 - 2 * x)
        val pa = Palette.of(a)
        val pb = Palette.of(b)
        lerpInto(out, pa, pb, e)
        // 해 ↔ 달은 하늘을 가로질러 미끄러지지 않게, 한쪽이 사라진 뒤 다른 쪽이 그 자리에 나타난다.
        if ((a == TimeOfDay.NIGHT) != (b == TimeOfDay.NIGHT)) {
            val src = if (e < .5f) pa else pb
            out.f[Palette.SUN_X] = src.f[Palette.SUN_X]
            out.f[Palette.SUN_Y] = src.f[Palette.SUN_Y]
            out.f[Palette.SUN_R] = src.f[Palette.SUN_R]
            out.f[Palette.SUN_A] = src.f[Palette.SUN_A] * abs(1 - 2 * e)
            out.c[Palette.SUN] = src.c[Palette.SUN]
        }
        return if (e < .5f) a else b
    }
}
