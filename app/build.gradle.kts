plugins {
    alias(libs.plugins.wellsketch.android.application)
    alias(libs.plugins.wellsketch.android.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.wellsketch.engineer"

    defaultConfig {
        applicationId = "com.wellsketch.engineer"
        versionCode = 3
        versionName = "0.2.0"
    }

    androidResources {
        // English-only product: do not package translations shipped by libraries.
        localeFilters += listOf("en")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // No signing configuration: release builds/signing require explicit owner approval.
        }
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:data"))
    implementation(project(":core:database"))
    implementation(project(":core:ui"))
    implementation(project(":domain:welldesign"))
    implementation(project(":feature:welldesign"))
    implementation(project(":feature:wellcalc"))
    implementation(project(":feature:ctcalc"))
    implementation(project(":feature:workover"))
    implementation(project(":feature:equipment"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
}
