package com.wellsketch.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Pure Kotlin/JVM library. Engineering models and calculations live in modules of this
 * type so that they stay independent from Android and are unit-testable on any JVM.
 */
class JvmLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.jvm")
            configureKotlinJvm()
        }
    }
}
