plugins {
    alias(libs.plugins.wellsketch.jvm.library)
}

dependencies {
    api(project(":core:model"))
    api(project(":core:engineering"))

    testImplementation(project(":core:data"))
}
