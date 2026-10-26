package com.kero.passingby

import android.content.Context
import com.kero.passingby.experience.JourneyMode
import com.kero.passingby.journey.PreviewCode
import com.kero.passingby.ui.journey.ModeAccess

internal class PreviewModeAccess(context: Context) : ModeAccess {
    // noBackupFilesDir keeps the grant local to this installation, outside cloud/device backups.
    private val grant = java.io.File(context.noBackupFilesDir, "preview-mode-grant")
    private val hash = BuildConfig.PREVIEW_CODE_SHA256
    private val verifier = PreviewCode(hash)
    override val publicModes = (BuildConfig.PUBLIC_MODES.split(',').mapNotNull(JourneyMode::fromId) + JourneyMode.TRAIN).toSet()
    override val codeEnabled get() = verifier.enabled
    override val unlocked get() = codeEnabled && runCatching { grant.readText() == hash }.getOrDefault(false)
    override fun unlock(code: String): Boolean {
        if (!verifier.matches(code)) return false
        return runCatching { grant.writeText(hash); true }.getOrDefault(false)
    }
    override fun lock() { grant.delete() }
}
