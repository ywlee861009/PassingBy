plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.kero.passingby.experience.api"
}

dependencies {
    testImplementation(libs.junit)
}
