plugins {
    alias(libs.plugins.wellsketch.android.feature)
}

android {
    namespace = "com.wellsketch.engineer.feature.wellcalc"
}

dependencies {
    implementation(project(":domain:wellcalc"))
}
