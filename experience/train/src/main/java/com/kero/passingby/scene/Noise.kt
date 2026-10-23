package com.kero.passingby.scene


import kotlin.math.floor

/**
 * 결정적 해시와 노이즈.
 * 같은 좌표·시드는 항상 같은 값을 내므로 풍경을 저장하지 않고 필요할 때마다 다시 계산한다.
 */
internal fun hash01(i: Int, seed: Int): Double {
    var x = ((i xor 0x27d4eb2d) * 0x85ebca6b.toInt()) xor ((seed + 0x165667b1) * 0xc2b2ae35.toInt())
    x = x xor (x ushr 15)
    x *= 0x2c1b3c6d
    x = x xor (x ushr 12)
    x *= 0x297a2d39
    x = x xor (x ushr 15)
    return (x.toLong() and 0xffffffffL) / 4294967296.0
}

internal fun valueNoise(x: Double, seed: Int): Double {
    val fl = floor(x)
    val i = fl.toInt()
    val f = x - fl
    val u = f * f * (3 - 2 * f)
    return hash01(i, seed) * (1 - u) + hash01(i + 1, seed) * u
}

internal fun fbm(x: Double, seed: Int, octaves: Int = 4): Double {
    var sum = 0.0
    var amp = 0.5
    var freq = 1.0
    var norm = 0.0
    for (o in 0 until octaves) {
        sum += valueNoise(x * freq, seed + o * 17) * amp
        norm += amp
        amp *= 0.5
        freq *= 2.03
    }
    return sum / norm
}

/** 스프라이트 마스크 생성용 순차 난수(mulberry32). */
internal class SeqRandom(seed: Int) {
    private var a = seed

    fun next(): Float {
        a += 0x6D2B79F5
        var t = a
        t = (t xor (t ushr 15)) * (t or 1)
        t = t xor (t + (t xor (t ushr 7)) * (t or 61))
        return (((t xor (t ushr 14)).toLong() and 0xffffffffL) / 4294967296.0).toFloat()
    }
}
