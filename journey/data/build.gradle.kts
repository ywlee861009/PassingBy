plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.kero.passingby.journey.data"
}

dependencies {
    testImplementation(libs.junit)
    implementation(project(":journey:domain"))
}
