plugins {
    alias(libs.plugins.wellsketch.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

android {
    namespace = "com.wellsketch.engineer.core.database"
}

room {
    // Exported Room schemas are committed (after the first build) to review migrations.
    // The Room Gradle plugin gives each variant its own KSP output and copies the result,
    // avoiding the debug/release race of a shared `room.schemaLocation` argument.
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    api(project(":core:data"))
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.kotlinx.coroutines.android)
}
