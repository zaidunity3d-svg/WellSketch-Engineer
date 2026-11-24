package com.wellsketch.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/**
 * Feature UI module: Android library + Compose + lifecycle/ViewModel, depending on the
 * shared model and UI modules. Feature modules contain no engineering logic; they render
 * state produced by the pure-Kotlin domain modules.
 */
class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("wellsketch.android.library")
            pluginManager.apply("wellsketch.android.compose")
            dependencies {
                add("implementation", project(":core:model"))
                add("implementation", project(":core:ui"))
                add("implementation", libs.library("androidx-lifecycle-runtime-compose"))
                add("implementation", libs.library("androidx-lifecycle-viewmodel-compose"))
                add("implementation", libs.library("androidx-lifecycle-viewmodel-ktx"))
                add("implementation", libs.library("kotlinx-coroutines-core"))
                add("testImplementation", libs.library("kotlinx-coroutines-test"))
            }
        }
    }
}
