package com.kero.passingby.experience.voyage

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/** Quiet procedural cabin loops. Playback is foreground-only and does not request audio focus. */
class AmbientLoop(private val kind: Kind) : VoyageAudio {
    enum class Kind { AIRPLANE, SHIP, SPACE }
    private var track: AudioTrack? = null
    override fun start(level: Int) {
        runCatching {
            if (track == null) {
                val sampleRate = 22050
                val samples = ShortArray(sampleRate * 4)
                val random = Random(37 + kind.ordinal)
                val frequency = when (kind) { Kind.AIRPLANE -> 72.0; Kind.SHIP -> 44.0; Kind.SPACE -> 55.0 }
                var filtered = 0.0
                for (i in samples.indices) {
                    filtered = filtered * .97 + (random.nextDouble() * 2 - 1) * .03
                    val t = i.toDouble() / sampleRate
                    val noise = filtered * if (kind == Kind.SHIP) 1.7 else .9
                    val hum = sin(2 * PI * frequency * t) * .13 + sin(2 * PI * frequency * 1.5 * t) * .035
                    val envelope = minOf(1.0, i / 500.0, (samples.lastIndex - i) / 500.0)
                    samples[i] = ((hum + noise) * envelope * 12000).toInt().toShort()
                }
                val created = AudioTrack.Builder()
                    .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                    .setAudioFormat(AudioFormat.Builder().setSampleRate(sampleRate)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                    .setBufferSizeInBytes(samples.size * 2).setTransferMode(AudioTrack.MODE_STATIC).build()
                if (created.state != AudioTrack.STATE_INITIALIZED) { created.release(); return }
                created.write(samples, 0, samples.size)
                created.setLoopPoints(0, samples.size, -1)
                track = created
            }
            setLevel(level)
            track?.play()
        }.onFailure { close() }
    }
    override fun setLevel(level: Int) { runCatching { track?.setVolume(floatArrayOf(.2f, .4f, .65f)[level.coerceIn(0, 2)]) } }
    override fun pause() { runCatching { track?.pause() } }
    override fun close() { runCatching { track?.release() }; track = null }
}
