package com.kero.passingby.telemetry

import android.content.Context
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics

/**
 * 사용 통계(Firebase Analytics)와 오류 보고(Crashlytics). Firebase는 이 파일에서만 부른다.
 *
 * - debug 빌드는 앱에서 전달한 collectionAllowed가 꺼져 있어 아무것도 보내지 않는다.
 * - 사용자가 설정에서 끄면 두 SDK의 수집을 끄고, 아직 보내지 않은 오류 보고도 지운다.
 * - 이름·연락처 같은 개인 정보는 넣지 않는다. 코스 id, 분, 여행 상태처럼 앱 안의 값만 쓴다.
 * - 프레임마다 불리는 곳에서는 쓰지 않는다. 상태가 바뀔 때만 부른다.
 */
object Telemetry {
    private var analytics: FirebaseAnalytics? = null
    private var crashlytics: FirebaseCrashlytics? = null
    /** 결제 콜백처럼 다른 스레드에서도 읽는다. */
    @Volatile private var enabled = false
    private var collectionAllowed = false

    /** 앱 프로세스가 뜰 때 한 번 부른다. [optIn]은 설정의 "사용 통계·오류 보고 보내기". */
    fun init(context: Context, optIn: Boolean, collectionAllowed: Boolean) {
        this.collectionAllowed = collectionAllowed
        analytics = FirebaseAnalytics.getInstance(context)
        crashlytics = FirebaseCrashlytics.getInstance()
        setEnabled(optIn)
    }

    fun setEnabled(optIn: Boolean) {
        enabled = collectionAllowed && optIn
        analytics?.setAnalyticsCollectionEnabled(enabled)
        crashlytics?.setCrashlyticsCollectionEnabled(enabled)
        if (!enabled) crashlytics?.deleteUnsentReports()
    }

    /**
     * 사용 통계 이벤트. 값은 문자열·정수·실수·참거짓만 쓴다(참거짓은 Analytics가 받지 않아 1/0으로 보낸다).
     * 같은 내용을 오류 보고의 기록에도 한 줄 남겨, 비정상 종료 직전에 무엇을 했는지 볼 수 있게 한다.
     */
    fun event(name: String, vararg params: Pair<String, Any>) {
        if (!enabled) return
        val bundle = Bundle()
        for ((key, value) in params) {
            when (value) {
                is String -> bundle.putString(key, value)
                is Int -> bundle.putLong(key, value.toLong())
                is Long -> bundle.putLong(key, value)
                is Boolean -> bundle.putLong(key, if (value) 1L else 0L)
                is Float -> bundle.putDouble(key, value.toDouble())
                is Double -> bundle.putDouble(key, value)
            }
        }
        analytics?.logEvent(name, bundle)
        crashlytics?.log(if (params.isEmpty()) name else "$name ${params.joinToString(" ") { "${it.first}=${it.second}" }}")
    }

    /** 사용자 속성(앱 언어, 소리 설정 등). 사용자별로 하나의 값만 남는다. */
    fun property(name: String, value: String) {
        if (!enabled) return
        analytics?.setUserProperty(name, value)
    }

    /** 비정상 종료 보고에 붙는 지금 상태. 여행 상태가 바뀔 때마다 덮어쓴다. */
    fun state(phase: String, course: String, minutes: Int, direction: Int) {
        if (!enabled) return
        crashlytics?.apply {
            setCustomKey("phase", phase)
            setCustomKey("course", course)
            setCustomKey("minutes", minutes)
            setCustomKey("direction", direction)
        }
    }

    /** 앱이 죽지는 않았지만 삼킨 실패. [where]는 어디서 났는지 찾기 쉬운 짧은 이름. */
    fun error(t: Throwable, where: String) {
        if (!enabled) return
        crashlytics?.apply {
            log("non-fatal: $where")
            recordException(t)
        }
    }
}
