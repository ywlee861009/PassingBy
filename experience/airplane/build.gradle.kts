plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}
android { namespace = "com.kero.passingby.experience.airplane" }
dependencies {
    implementation(project(":experience:api"))
    implementation(project(":experience:voyage"))
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.junit)
}
