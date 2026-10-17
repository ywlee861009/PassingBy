package com.kero.passingby.locale

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

/**
 * 앱 언어. 기본은 기기 언어를 따르고([SYSTEM]), 설정에서 고르면 그 언어로 고정한다.
 * 기기 언어가 한국어·영어가 아니면 기본 문구(영어)로 보인다.
 *
 * Android 13 이상은 시스템의 앱별 언어(LocaleManager)에 맡긴다. 시스템 설정 → 앱 언어와 같은 값이고, 바꾸면 시스템이 화면을 다시 만든다.
 * 그 아래는 직접 저장해 두고, 액티비티를 만들 때([wrap])와 알림 문구를 만들 때([localized]) 덮어씌운다.
 */
enum class AppLanguage(val tag: String?) {
    SYSTEM(null),
    KOREAN("ko"),
    ENGLISH("en");

    companion object {
        private const val PREFS = "language"
        private const val KEY = "tag"

        fun current(ctx: Context): AppLanguage {
            val tag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ctx.getSystemService(LocaleManager::class.java)?.applicationLocales
                    ?.takeUnless { it.isEmpty }?.get(0)?.language
            } else {
                ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
            }
            return entries.firstOrNull { it.tag != null && it.tag == tag } ?: SYSTEM
        }

        /** 언어를 바꾸고 화면을 그 언어로 다시 만든다. */
        fun set(activity: Activity, language: AppLanguage) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                activity.getSystemService(LocaleManager::class.java)?.applicationLocales =
                    language.tag?.let { LocaleList.forLanguageTags(it) } ?: LocaleList.getEmptyLocaleList()
            } else {
                activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, language.tag).apply()
                activity.recreate()
            }
        }

        /** 액티비티의 attachBaseContext에서 부른다. Android 13 이상은 시스템이 이미 맞춰 주므로 그대로 둔다. */
        fun wrap(base: Context): Context =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) base else localized(base)

        /** 고른 언어로 문구를 읽는 Context. 알림처럼 액티비티 밖에서 문구를 만들 때 쓴다. */
        fun localized(ctx: Context): Context {
            val tag = current(ctx).tag ?: return ctx
            val config = Configuration(ctx.resources.configuration)
            config.setLocale(Locale.forLanguageTag(tag))
            return ctx.createConfigurationContext(config)
        }
    }
}
