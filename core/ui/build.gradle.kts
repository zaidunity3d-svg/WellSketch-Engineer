plugins {
    alias(libs.plugins.wellsketch.android.library)
    alias(libs.plugins.wellsketch.android.compose)
}

android {
    namespace = "com.wellsketch.engineer.core.ui"
}

dependencies {
    api(project(":core:model"))
    api(project(":core:engineering"))
    api(project(":core:schematic"))
}
