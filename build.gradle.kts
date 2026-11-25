// Root build file. Plugins are declared here with `apply false` so that every module
// shares one plugin classloader; modules apply them through convention plugins.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.room) apply false
}

// ---------------------------------------------------------------------------------------
// Build policy guard (CLAUDE.md rule 4): no APK/AAB may be built, signed or exported until
// the project owner explicitly approves. Packaging tasks of :app fail fast unless the
// property `wellsketch.ownerApprovedApkBuild=true` is passed on the command line.
// Compilation and unit-test tasks are not affected.
// ---------------------------------------------------------------------------------------
val apkPackagingTaskPattern =
    Regex("^(assemble|bundle|package|install|sign|zipalign)(Debug|Release)?(Bundle|Apk)?$")

gradle.taskGraph.whenReady {
    val approved = providers.gradleProperty("wellsketch.ownerApprovedApkBuild").orNull == "true"
    if (approved) return@whenReady
    val blocked = allTasks.filter { task ->
        task.project.path == ":app" && apkPackagingTaskPattern.matches(task.name)
    }
    if (blocked.isNotEmpty()) {
        throw GradleException(
            "APK/AAB packaging is blocked by project policy (see README 'Build policy'). " +
                "Blocked tasks: ${blocked.joinToString { it.path }}. " +
                "Only the project owner may approve an APK build."
        )
    }
}
