import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `kotlin-dsl`
}

group = "com.wellsketch.buildlogic"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    // compileOnly: the plugin implementations are provided by the root build classpath.
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.compose.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("jvmLibrary") {
            id = "wellsketch.jvm.library"
            implementationClass = "com.wellsketch.buildlogic.JvmLibraryConventionPlugin"
        }
        register("androidLibrary") {
            id = "wellsketch.android.library"
            implementationClass = "com.wellsketch.buildlogic.AndroidLibraryConventionPlugin"
        }
        register("androidCompose") {
            id = "wellsketch.android.compose"
            implementationClass = "com.wellsketch.buildlogic.AndroidComposeConventionPlugin"
        }
        register("androidFeature") {
            id = "wellsketch.android.feature"
            implementationClass = "com.wellsketch.buildlogic.AndroidFeatureConventionPlugin"
        }
        register("androidApplication") {
            id = "wellsketch.android.application"
            implementationClass = "com.wellsketch.buildlogic.AndroidApplicationConventionPlugin"
        }
    }
}
