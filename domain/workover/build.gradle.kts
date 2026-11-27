plugins {
    alias(libs.plugins.wellsketch.jvm.library)
}

dependencies {
    api(project(":core:model"))
    api(project(":core:engineering"))
    api(project(":core:schematic"))

    testImplementation(project(":core:data"))
    testImplementation(project(":domain:welldesign"))
}
