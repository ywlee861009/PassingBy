package com.kero.passingby

import android.app.Application
import com.kero.passingby.locale.AppLanguage
import com.kero.passingby.telemetry.Telemetry

/** 프로세스가 뜰 때 사용 통계·오류 보고 설정을 맞춘다. 화면 없이 알람만 받는 경우에도 거친다. */
class PassingByApp : Application() {
    lateinit var graph: AppGraph
        private set

    private var launchArtworkClaimed = false

    /** 프로세스에서 처음 여는 여행 화면에만 시작 그림을 보여 준다. 회전·앱 복귀 때는 건너뛴다. */
    fun claimLaunchArtwork(): Boolean {
        if (launchArtworkClaimed) return false
        launchArtworkClaimed = true
        return true
    }

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this)
        Telemetry.init(this, graph.store.telemetryOn, BuildConfig.TELEMETRY)
        Telemetry.property("app_language", AppLanguage.current(this).name.lowercase())
    }
}
