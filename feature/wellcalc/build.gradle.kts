plugins {
    alias(libs.plugins.wellsketch.android.feature)
}

android {
    namespace = "com.wellsketch.engineer.feature.wellcalc"
}

dependencies {
    implementation(project(":core:data"))
    implementation(project(":domain:wellcalc"))
}
