package com.kero.passingby.experience.voyage

/** Presentation-owned audio. Scene samples never advance the journey's clock. */
interface VoyageAudio : AutoCloseable {
    fun start(level: Int)
    fun setLevel(level: Int)
    fun update(scene: VoyageScene, running: Boolean) = Unit
    fun pause()
}
