import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

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
        // 개발용 패널·HUD·실행 옵션. 성능 측정용 release에서도 쓰므로 지금은 둘 다 켠다. 출시 빌드에서는 false로.
        buildConfigField("boolean", "DEV_TOOLS", "true")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // 성능 측정용으로 debug 키로 서명한다. 출시 전에 실제 서명 설정으로 교체.
            signingConfig = signingConfigs.getByName("debug")
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
