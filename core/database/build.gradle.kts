plugins {
    alias(libs.plugins.wellsketch.android.library)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.wellsketch.engineer.core.database"
}

ksp {
    // Exported Room schemas are committed (after the first build) to review migrations.
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    api(project(":core:data"))
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.kotlinx.coroutines.android)
}
