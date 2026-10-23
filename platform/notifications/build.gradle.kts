plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.kero.passingby.platform.notifications"
}

dependencies {
    testImplementation(libs.junit)
    implementation(project(":platform:telemetry"))
    implementation(libs.androidx.core.ktx)
}
