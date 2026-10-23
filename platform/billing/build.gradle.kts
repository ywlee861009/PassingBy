plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.kero.passingby.platform.billing"
}

dependencies {
    testImplementation(libs.junit)
    implementation(project(":platform:telemetry"))
    implementation(libs.billing)
}
