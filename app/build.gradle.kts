import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Play 업로드 키(kero-studio). 경로와 비밀번호는 커밋하지 않는 local.properties의 kero.upload.*에 둔다.
val uploadKey: Properties? = rootProject.file("local.properties").takeIf { it.exists() }
    ?.let { f -> Properties().apply { f.inputStream().use { load(it) } } }
    ?.takeIf { it.getProperty("kero.upload.storeFile") != null }

android {
    // applicationId는 Play Console에 올린 뒤에는 바꿀 수 없다.
    namespace = "com.kero.passingby"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.kero.passingby"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0-prototype"
    }

    signingConfigs {
        uploadKey?.let { p ->
            create("upload") {
                storeFile = file(p.getProperty("kero.upload.storeFile"))
                storePassword = p.getProperty("kero.upload.storePassword")
                keyAlias = p.getProperty("kero.upload.keyAlias")
                keyPassword = p.getProperty("kero.upload.keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            // 개발용 패널·HUD·실행 옵션·"모든 코스 열기". 사용자에게 가는 release에서는 끈다.
            buildConfigField("boolean", "DEV_TOOLS", "true")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // 업로드 키가 없는 환경에서는 debug 키로 서명한다. 이 번들은 Play에 올릴 수 없다.
            signingConfig = signingConfigs.findByName("upload") ?: signingConfigs.getByName("debug")
            buildConfigField("boolean", "DEV_TOOLS", "false")
        }
    }
    androidResources {
        // 앱 문구는 영어(기본)와 한국어뿐이다. 라이브러리의 다른 언어 문구를 빼서 한 화면에 언어가 섞이지 않게 한다.
        localeFilters += listOf("en", "ko")
    }
    bundle {
        // 앱 안에서 언어를 고를 수 있으므로 언어별로 나누지 않는다. 나누면 Play가 기기 언어 리소스만 설치해,
        // 영어 기기에서 한국어를 골라도 한국어 문구가 없어 영어로 보인다.
        language {
            enableSplit = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.window)
    implementation(libs.billing)
}
