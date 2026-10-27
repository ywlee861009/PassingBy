package com.kero.passingby.experience.spaceship

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.kero.passingby.experience.voyage.VoyageAudio
import com.kero.passingby.experience.voyage.VoyageScene

/** Streaming playback owned by the foreground presentation, preserving the user's sound setting. */
internal class SpaceAudio : VoyageAudio {
    @Volatile private var profile = SpaceSoundProfile.IDLE
    @Volatile private var volume = .72
    @Volatile private var enabled = false
    @Volatile private var closed = false
    @Volatile private var output: AudioTrack? = null
    private val gate = Object()
    private var worker: Thread? = null

    internal val playing get() = output?.playState == AudioTrack.PLAYSTATE_PLAYING
    internal val playedFrames get() = output?.playbackHeadPosition ?: 0

    @Synchronized override fun start(level: Int) {
        if (closed) return
        setLevel(level); enabled = true
        synchronized(gate) { gate.notifyAll() }
        if (worker?.isAlive == true) return
        worker = Thread(::run, "space-cabin-audio").apply { isDaemon = true; start() }
    }

    override fun setLevel(level: Int) { volume = doubleArrayOf(.48, .72, 1.0)[level.coerceIn(0, 2)] }
    override fun update(scene: VoyageScene, running: Boolean) {
        profile = SpaceSoundProfile.at(scene.progress, scene.durationSeconds,
            scene.course.stations.size, scene.direction, running)
    }
    override fun pause() { enabled = false }
    override fun close() {
        closed = true; enabled = false
        synchronized(gate) { gate.notifyAll() }
        worker?.interrupt()
    }

    private fun run() {
        var track: AudioTrack? = null
        try {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO)
            val min = AudioTrack.getMinBufferSize(SpaceSynth.RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
            val created = AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                .setAudioFormat(AudioFormat.Builder().setSampleRate(SpaceSynth.RATE)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setBufferSizeInBytes(maxOf(min, 4096)).setTransferMode(AudioTrack.MODE_STREAM).build()
            track = created
            check(created.state == AudioTrack.STATE_INITIALIZED)
            output = created
            val samples = ShortArray(512)
            var synth = SpaceSynth()
            var active = false
            while (!closed) {
                if (!enabled) {
                    if (active) { created.pause(); created.flush(); active = false; synth = SpaceSynth() }
                    synchronized(gate) { while (!enabled && !closed) gate.wait() }
                    continue
                }
                if (!active) { created.play(); active = true }
                synth.render(samples, profile, volume)
                var offset = 0
                while (offset < samples.size && enabled && !closed) {
                    val written = created.write(samples, offset, samples.size - offset, AudioTrack.WRITE_NON_BLOCKING)
                    check(written >= 0) { "Space audio write failed: $written" }
                    if (written == 0) Thread.sleep(4) else offset += written
                }
            }
        } catch (_: InterruptedException) {
            // Closing the presentation wakes the worker immediately, including while muted.
        } catch (error: RuntimeException) {
            // Audio device loss must not interrupt the focus session; the next start may retry.
            android.util.Log.w("PassingBySpaceAudio", "Unable to play cabin audio", error)
        } finally {
            runCatching { track?.pause() }; runCatching { track?.flush() }; runCatching { track?.release() }
            output = null
            synchronized(this) { worker = null }
        }
    }
}
