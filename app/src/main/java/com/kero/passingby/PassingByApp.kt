package com.kero.passingby

import android.app.Application
import com.kero.passingby.locale.AppLanguage
import com.kero.passingby.session.TripStore
import com.kero.passingby.telemetry.Telemetry

/** 프로세스가 뜰 때 사용 통계·오류 보고 설정을 맞춘다. 화면 없이 알람만 받는 경우에도 거친다. */
class PassingByApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Telemetry.init(this, TripStore(this).telemetryOn)
        Telemetry.property("app_language", AppLanguage.current(this).name.lowercase())
    }
}
