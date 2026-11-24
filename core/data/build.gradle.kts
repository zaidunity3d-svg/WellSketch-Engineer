plugins {
    alias(libs.plugins.wellsketch.jvm.library)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    api(project(":core:model"))
    api(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(project(":core:engineering"))
    testImplementation(libs.kotlinx.coroutines.test)
}
