package com.passingby.app.scene

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin

/**
 * 실제 시각 → 하늘 팔레트.
 * 해 뜨고 지는 시각을 날짜·위치로 계산하고, 그 시각을 기준으로 네 시간대 팔레트를 이어 붙인다.
 * 위치 권한을 연동하기 전에는 서울 좌표를 쓴다.
 */
class SkyClock(
    private val latitude: Double = 37.57,
    private val longitude: Double = 126.98,
    private val zone: ZoneId = ZoneId.systemDefault(),
) {
    private var cachedDate: LocalDate? = null
    /** 그날의 키프레임: 하루 중 분, 팔레트. */
    private val keyMin = DoubleArray(KEYS.size)

    /** 마지막으로 계산한 시각에 가장 가까운 시간대. */
    var nearest = TimeOfDay.DAY
        private set

    /** [epochMillis] 시각의 하늘 팔레트를 [out]에 채운다. */
    fun paletteAt(epochMillis: Long, out: Palette) {
        val t = Instant.ofEpochMilli(epochMillis).atZone(zone)
        val date = t.toLocalDate()
        if (date != cachedDate) computeKeys(date)
        val m = t.hour * 60 + t.minute + t.second / 60.0 + t.nano / 6e10
        if (m <= keyMin[0] || m >= keyMin[KEYS.size - 1]) {
            out.copyFrom(Palette.of(TimeOfDay.NIGHT))
            nearest = TimeOfDay.NIGHT
            return
        }
        var i = 0
        while (m >= keyMin[i + 1]) i++
        val a = KEYS[i]
        val b = KEYS[i + 1]
        val x = ((m - keyMin[i]) / (keyMin[i + 1] - keyMin[i])).toFloat()
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
        nearest = if (e < .5f) a else b
    }

    /**
     * 해 뜨고 지는 시각(현지 시각, 분) 근사 계산. 오차는 수 분 수준으로 하늘 색에는 충분하다.
     * 적위·균시차는 일반적인 근사식, 고도 −0.833°(대기 굴절·태양 반지름)를 일출·일몰 기준으로 쓴다.
     */
    private fun computeKeys(date: LocalDate) {
        cachedDate = date
        val n = date.dayOfYear
        val decl = Math.toRadians(23.44) * sin(2 * PI * (284 + n) / 365.0)
        val lat = Math.toRadians(latitude)
        val cosW = (sin(Math.toRadians(-.833)) - sin(lat) * sin(decl)) / (cos(lat) * cos(decl))
        val halfDayMin = Math.toDegrees(acos(cosW.coerceIn(-1.0, 1.0))) * 4
        val b = 2 * PI * (n - 81) / 364.0
        val eot = 9.87 * sin(2 * b) - 7.53 * cos(b) - 1.5 * sin(b)
        val offsetMin = zone.rules.getOffset(date.atTime(12, 0).atZone(zone).toInstant()).totalSeconds / 60.0
        val noon = 720 - 4 * longitude - eot + offsetMin
        val rise = noon - halfDayMin
        val set = noon + halfDayMin
        for (i in KEYS.indices) keyMin[i] = (if (i < 4) rise else set) + OFFSETS[i]
        // 극지방처럼 키프레임이 뒤엉키는 경우를 막는다(한국에서는 일어나지 않음).
        for (i in 1 until KEYS.size) if (keyMin[i] <= keyMin[i - 1]) keyMin[i] = keyMin[i - 1] + 1
    }

    private companion object {
        /** 일출(앞 넷)·일몰(뒤 넷) 기준 키프레임과 그때의 시간대. */
        val KEYS = arrayOf(
            TimeOfDay.NIGHT, TimeOfDay.DAWN, TimeOfDay.DAWN, TimeOfDay.DAY,
            TimeOfDay.DAY, TimeOfDay.DUSK, TimeOfDay.DUSK, TimeOfDay.NIGHT,
        )
        val OFFSETS = doubleArrayOf(-70.0, -20.0, 30.0, 90.0, -90.0, -30.0, 15.0, 60.0)
    }
}
