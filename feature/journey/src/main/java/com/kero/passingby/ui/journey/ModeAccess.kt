package com.kero.passingby.ui.journey

import com.kero.passingby.experience.JourneyMode

/** App-owned access policy. Public launch and preview entry share the same mode picker. */
interface ModeAccess {
    val publicModes: Set<JourneyMode>
    val codeEnabled: Boolean
    val unlocked: Boolean
    fun unlock(code: String): Boolean
    fun lock()

    companion object {
        val TrainOnly = object : ModeAccess {
            override val publicModes = setOf(JourneyMode.TRAIN)
            override val codeEnabled = false
            override val unlocked = false
            override fun unlock(code: String) = false
            override fun lock() = Unit
        }
    }
}
