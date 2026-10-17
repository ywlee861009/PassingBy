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
