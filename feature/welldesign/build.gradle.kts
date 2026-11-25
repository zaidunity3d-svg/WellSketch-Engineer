plugins {
    alias(libs.plugins.wellsketch.android.feature)
}

android {
    namespace = "com.wellsketch.engineer.feature.welldesign"
    testOptions {
        unitTests {
            // Robolectric UI tests need merged resources and the test activity manifest.
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    implementation(project(":core:data"))
    implementation(project(":domain:welldesign"))

    // Compose UI tests on the JVM (Robolectric). They live in src/testDebug so that the
    // test-only manifest (ui-test-manifest) is never part of a release build.
    testDebugImplementation(libs.robolectric)
    testDebugImplementation(platform(libs.androidx.compose.bom))
    testDebugImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
