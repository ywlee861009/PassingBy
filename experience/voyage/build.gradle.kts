plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}
android { namespace = "com.kero.passingby.experience.voyage" }
dependencies {
    implementation(project(":experience:api"))
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.junit)
}
