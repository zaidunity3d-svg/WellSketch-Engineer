plugins {
    alias(libs.plugins.wellsketch.android.feature)
}

android {
    namespace = "com.wellsketch.engineer.feature.workover"
}

dependencies {
    implementation(project(":core:data"))
    implementation(project(":domain:workover"))
}
