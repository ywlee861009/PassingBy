package com.kero.passingby.audio

import com.kero.passingby.journey.WindowEventKind
import kotlin.math.PI
import kotlin.math.sin

/** Quiet, progress-driven envelopes over the carriage's existing noise and hum. */
internal class WindowEventSound(rate: Int) {
    private val follow = 1f / (rate * .025f)
    private var tapGain = 0f
    private var windGain = 0f
    private var rumbleGain = 0f
    private var humGain = 0f
    private var tap = 0f
    private var wind = 0f
    private var rumble = 0f
    private var hum = 0f

    fun update(kind: WindowEventKind?, p: Float): Boolean {
        tapGain = 0f
        windGain = 0f
        rumbleGain = 0f
        humGain = 0f
        when (kind) {
            WindowEventKind.WINDOW_DROPS -> {
                tapGain = .032f * (pulse(p, .12f, .005f, .023f) + pulse(p, .31f, .005f, .022f) +
                    pulse(p, .44f, .006f, .021f))
            }
            WindowEventKind.SEA_BIRDS, WindowEventKind.DARJEELING_SUNBREAK -> {
                windGain = .025f * swell(p, .14f, .84f)
            }
            WindowEventKind.PASSING_TRAIN -> {
                val passing = swell(p, .14f, .88f)
                val wheels = (.75 + .25 * sin(p * 16 * PI)).toFloat()
                windGain = .048f * passing
                rumbleGain = .27f * passing * wheels
                humGain = .40f * passing
            }
            WindowEventKind.METEOR -> {
                val approach = swell(p, .03f, .43f) * (p / .43f).coerceIn(0f, 1f)
                val impact = pulse(p, .415f, .010f, .15f)
                windGain = .072f * approach + .030f * impact
                rumbleGain = .58f * impact
                humGain = 1.7f * impact
            }
            else -> Unit // A glint, birds, a shooting star and deer can pass silently.
        }
        if (tapGain + windGain + rumbleGain + humGain + tap + wind + rumble + hum >= .0001f) return true
        tap = 0f
        wind = 0f
        rumble = 0f
        hum = 0f
        return false
    }

    fun sample(highNoise: Float, lowWind: Float, lowRumble: Float, cabinHum: Float): Float {
        tap += (tapGain - tap) * follow
        wind += (windGain - wind) * follow
        rumble += (rumbleGain - rumble) * follow
        hum += (humGain - hum) * follow
        return (highNoise * tap + lowWind * wind + lowRumble * rumble + cabinHum * hum)
            .coerceIn(-.06f, .06f)
    }

    private fun swell(p: Float, start: Float, end: Float): Float {
        val t = ((p - start) / (end - start)).coerceIn(0f, 1f)
        val arch = 4f * t * (1f - t)
        return arch * arch
    }

    private fun pulse(p: Float, start: Float, attack: Float, decay: Float): Float {
        val age = p - start
        val rise = (age / attack).coerceIn(0f, 1f)
        val tail = (1f - (age - attack) / decay).coerceIn(0f, 1f)
        return rise * tail * tail
    }
}
