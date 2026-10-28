import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    // app/google-services.json이 있어야 빌드된다. 공개 저장소라 커밋하지 않는다(.gitignore). CI는 Secret으로 받는다.
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics)
}

val localProperties = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}

// Play 업로드 키(kero-studio). 로컬은 커밋하지 않는 local.properties의 kero.upload.*를,
// CI는 KERO_UPLOAD_* 환경변수를 읽는다. 상대 경로는 저장소 루트 기준이다.
fun uploadKeyValue(localKey: String, envKey: String): String? =
    localProperties.getProperty("kero.upload.$localKey")?.takeIf { it.isNotBlank() }
        ?: System.getenv(envKey)?.takeIf { it.isNotBlank() }

// 사용 통계·오류 보고(Firebase). release는 켜고, debug는 개발 중 기록이 섞이지 않게 끈다.
// debug에서 확인하려면 -Ppassingby.telemetry=true 로 빌드한다.
val debugTelemetry = providers.gradleProperty("passingby.telemetry").orNull == "true"

// Only train is public. Other modes require the private preview code, even in release builds.
val previewCodeHash = providers.gradleProperty("passingby.previewCodeSha256").orNull
    ?: System.getenv("PASSINGBY_PREVIEW_CODE_SHA256")
    ?: localProperties.getProperty("passingby.preview.code.sha256", "")
require(previewCodeHash.isEmpty() || previewCodeHash.matches(Regex("[0-9a-f]{64}"))) { "Invalid preview code SHA-256" }
require(providers.gradleProperty("passingby.publicModes").orNull.let { it == null || it == "train" }) {
    "Only train is public. Other modes must stay behind the private preview code."
}

val uploadKeyFile: File? = uploadKeyValue("storeFile", "KERO_UPLOAD_STORE_FILE")
    ?.let { rootProject.file(it) }?.takeIf { it.exists() }

// versionCode와 versionName. CI는 직전 배포 태그를 보고 한 단계 올린 값을 PASSINGBY_VERSION_*로 넘긴다.
// 로컬 빌드는 version.properties의 첫 버전을 쓴다(scripts/resolve_release_version.py 참고).
val versionProperties = Properties().apply {
    rootProject.file("version.properties").inputStream().use { load(it) }
}

fun versionInt(key: String, min: Int): Int =
    versionProperties.getProperty(key)?.toIntOrNull()?.takeIf { it >= min }
        ?: throw GradleException("version.properties의 $key 는 $min 이상의 정수여야 한다")

val versionMajor = versionInt("version_major", 1)
val versionMinor = versionInt("version_minor", 0)
val firstVersionCode = versionInt("first_version_code", 1)

val resolvedVersionCode: Int = System.getenv("PASSINGBY_VERSION_CODE")?.takeIf { it.isNotBlank() }
    ?.let { raw ->
        raw.toIntOrNull()?.takeIf { it >= firstVersionCode }
            ?: throw GradleException("PASSINGBY_VERSION_CODE 는 $firstVersionCode 이상의 정수여야 한다: '$raw'")
    }
    ?: firstVersionCode

val resolvedVersionName: String = System.getenv("PASSINGBY_VERSION_NAME")?.takeIf { it.isNotBlank() }
    ?.also { name ->
        val patch = Regex("^$versionMajor\\.$versionMinor\\.(\\d{4})$").matchEntire(name)
            ?.groupValues?.get(1)?.toIntOrNull()
        if (patch == null || patch < 1) {
            throw GradleException("PASSINGBY_VERSION_NAME 은 $versionMajor.$versionMinor.0001 형식이어야 한다: '$name'")
        }
    }
    ?: "$versionMajor.$versionMinor.0001"

android {
    // applicationId는 Play Console에 올린 뒤에는 바꿀 수 없다.
    namespace = "com.kero.passingby"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.kero.passingby"
        buildConfigField("String", "PREVIEW_CODE_SHA256", "\"$previewCodeHash\"")
        minSdk = 26
        targetSdk = 36
        versionCode = resolvedVersionCode
        versionName = resolvedVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        uploadKeyFile?.let { f ->
            create("upload") {
                storeFile = f
                storePassword = uploadKeyValue("storePassword", "KERO_UPLOAD_STORE_PASSWORD")
                keyAlias = uploadKeyValue("keyAlias", "KERO_UPLOAD_KEY_ALIAS")
                keyPassword = uploadKeyValue("keyPassword", "KERO_UPLOAD_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            // 개발용 패널·HUD·실행 옵션·"모든 코스 열기". 사용자에게 가는 release에서는 끈다.
            buildConfigField("boolean", "DEV_TOOLS", "true")
            buildConfigField("boolean", "TELEMETRY", debugTelemetry.toString())
            manifestPlaceholders["telemetry"] = debugTelemetry.toString()
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // 업로드 키가 없는 환경에서는 debug 키로 서명한다. 이 번들은 Play에 올릴 수 없다.
            signingConfig = signingConfigs.findByName("upload") ?: signingConfigs.getByName("debug")
            buildConfigField("boolean", "DEV_TOOLS", "false")
            buildConfigField("boolean", "TELEMETRY", "true")
            manifestPlaceholders["telemetry"] = "true"
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
    implementation(project(":journey:domain"))
    implementation(project(":journey:data"))
    implementation(project(":feature:journey"))
    implementation(project(":experience:api"))
    implementation(project(":experience:train"))
    implementation(project(":experience:airplane"))
    implementation(project(":experience:ship"))
    implementation(project(":experience:spaceship"))
    implementation(project(":platform:notifications"))
    implementation(project(":platform:billing"))
    implementation(project(":platform:telemetry"))
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.junit)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.window)
}
