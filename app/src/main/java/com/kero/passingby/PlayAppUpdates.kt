package com.kero.passingby

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import com.kero.passingby.feature.journey.R
import com.kero.passingby.ui.journey.AppUpdateStage
import com.kero.passingby.ui.journey.AppUpdateUiState

/** Activity에 연결되는 Play 유연 업데이트. 다운로드 후 재시작은 사용자가 고른다. */
internal class PlayAppUpdates(
    private val activity: ComponentActivity,
    savedState: Bundle?,
    private val docked: () -> Boolean,
) {
    private val manager = AppUpdateManagerFactory.create(activity.applicationContext)
    private val options = AppUpdateOptions.defaultOptions(AppUpdateType.FLEXIBLE)
    private val preferences = activity.getSharedPreferences("play_update", Context.MODE_PRIVATE)
    private var versionCode = savedState?.getInt(REQUESTED_VERSION) ?: 0
    private var awaitingConsent = versionCode > 0
    private var completing = false
    private var infoSequence = 0

    var uiState by mutableStateOf<AppUpdateUiState?>(
        if (awaitingConsent) AppUpdateUiState(AppUpdateStage.REQUESTING) else null,
    )
        private set

    private val launcher = activity.registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        awaitingConsent = false
        if (uiState?.stage == AppUpdateStage.REQUESTING) {
            if (result.resultCode == Activity.RESULT_OK) {
                show(AppUpdateStage.DOWNLOADING)
            } else {
                show(AppUpdateStage.AVAILABLE)
                if (result.resultCode != Activity.RESULT_CANCELED) showFailure()
            }
        }
        refresh()
    }

    private val listener = InstallStateUpdatedListener { state ->
        when (val status = state.installStatus()) {
            InstallStatus.CANCELED, InstallStatus.FAILED -> {
                infoSequence++
                val installing = uiState?.stage == AppUpdateStage.INSTALLING
                show(if (installing) AppUpdateStage.READY else AppUpdateStage.AVAILABLE)
                if (status == InstallStatus.FAILED) showFailure()
            }
            else -> applyInstallStatus(status)
        }
    }

    fun onStart() = manager.registerListener(listener)
    fun onStop() = manager.unregisterListener(listener)

    fun saveState(outState: Bundle) {
        if (awaitingConsent) outState.putInt(REQUESTED_VERSION, versionCode)
    }

    fun refresh() {
        if (awaitingConsent || completing || uiState?.stage == AppUpdateStage.REQUESTING) return
        queryInfo { info ->
            rememberVersion(info)
            if (!applyInstallStatus(info.installStatus())) {
                show(if (canRequest(info)) AppUpdateStage.AVAILABLE else null)
            }
        }
    }

    fun dismissPrompt() {
        val stage = uiState?.stage ?: return
        val key = dismissKey(stage) ?: return
        preferences.edit().putInt(key, versionCode).apply()
        show(stage)
    }

    fun act() {
        if (!canInteract()) return
        when (uiState?.stage) {
            AppUpdateStage.AVAILABLE -> request()
            AppUpdateStage.READY -> install()
            else -> Unit
        }
    }

    fun cancelRequest() {
        if (awaitingConsent || uiState?.stage != AppUpdateStage.REQUESTING) return
        infoSequence++
        show(AppUpdateStage.AVAILABLE)
    }

    private fun request() {
        dismissPrompt()
        show(AppUpdateStage.REQUESTING)
        // Play에서 받은 AppUpdateInfo는 실행을 재시도할 때마다 새로 받아야 한다.
        queryInfo(onFailure = ::requestFailed) { info ->
            rememberVersion(info)
            if (applyInstallStatus(info.installStatus())) return@queryInfo
            if (!canRequest(info)) {
                show(null)
                showFailure()
                return@queryInfo
            }
            if (!canInteract()) {
                show(AppUpdateStage.AVAILABLE)
                return@queryInfo
            }
            preferences.edit().putInt(DISMISSED_OFFER, versionCode).apply()
            awaitingConsent = true
            val launched = runCatching { manager.startUpdateFlowForResult(info, launcher, options) }.getOrDefault(false)
            if (!launched) requestFailed()
        }
    }

    private fun requestFailed() {
        awaitingConsent = false
        show(AppUpdateStage.AVAILABLE)
        showFailure()
    }

    private fun install() {
        dismissPrompt()
        infoSequence++
        completing = true
        show(AppUpdateStage.INSTALLING)
        manager.completeUpdate().addOnCompleteListener { result ->
            completing = false
            if (activity.isDestroyed || uiState?.stage != AppUpdateStage.INSTALLING) return@addOnCompleteListener
            if (!result.isSuccessful) {
                show(AppUpdateStage.READY)
                showFailure()
            }
        }
    }

    private fun queryInfo(onFailure: () -> Unit = {}, onSuccess: (AppUpdateInfo) -> Unit) {
        val sequence = ++infoSequence
        manager.appUpdateInfo.addOnCompleteListener { result ->
            if (sequence != infoSequence || activity.isDestroyed) return@addOnCompleteListener
            if (result.isSuccessful) onSuccess(result.result) else onFailure()
        }
    }

    private fun canRequest(info: AppUpdateInfo) =
        info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE && info.isUpdateTypeAllowed(options)

    private fun canInteract() = docked() && activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)

    private fun rememberVersion(info: AppUpdateInfo) {
        val available = info.availableVersionCode()
        if (available > 0) versionCode = available
    }

    private fun applyInstallStatus(status: Int): Boolean {
        val stage = when (status) {
            InstallStatus.PENDING, InstallStatus.DOWNLOADING -> AppUpdateStage.DOWNLOADING
            InstallStatus.DOWNLOADED -> AppUpdateStage.READY
            InstallStatus.INSTALLING -> AppUpdateStage.INSTALLING
            InstallStatus.INSTALLED -> null
            else -> return false
        }
        // 새 설치 상태가 늦게 도착한 조회 결과에 의해 되돌아가지 않게 한다.
        infoSequence++
        show(stage)
        return true
    }

    private fun show(stage: AppUpdateStage?) {
        uiState = stage?.let {
            val key = dismissKey(it)
            AppUpdateUiState(it, showPrompt = key != null && preferences.getInt(key, -1) != versionCode)
        }
    }

    private fun showFailure() {
        if (activity.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            Toast.makeText(activity, R.string.app_update_failed, Toast.LENGTH_LONG).show()
        }
    }

    private fun dismissKey(stage: AppUpdateStage) = when (stage) {
        AppUpdateStage.AVAILABLE -> DISMISSED_OFFER
        AppUpdateStage.READY -> DISMISSED_READY
        else -> null
    }

    private companion object {
        const val REQUESTED_VERSION = "play_update_requested_version"
        const val DISMISSED_OFFER = "dismissed_offer_version"
        const val DISMISSED_READY = "dismissed_ready_version"
    }
}
