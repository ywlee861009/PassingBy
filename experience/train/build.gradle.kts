plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.kero.passingby.experience.train"
}

dependencies {
    testImplementation(libs.junit)
    implementation(project(":experience:api"))
    implementation(project(":journey:domain"))
    implementation(project(":platform:telemetry"))
    implementation(libs.androidx.core.ktx)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.junit)
}
