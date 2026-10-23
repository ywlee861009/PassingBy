plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.kero.passingby.feature.journey"
    buildFeatures { compose = true }
}

dependencies {
    testImplementation(libs.junit)
    implementation(project(":journey:domain"))
    implementation(project(":experience:api"))
    implementation(project(":platform:billing"))
    implementation(project(":platform:telemetry"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.window)
}
