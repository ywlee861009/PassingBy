plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.kero.passingby.experience.api"
}

dependencies {
    api(project(":journey:domain"))
    implementation(libs.androidx.core.ktx)
    testImplementation(libs.junit)
}
