pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "WellSketchEngineer"

// Android application shell (navigation, home screen, dependency wiring)
include(":app")

// Shared engineering foundation (pure Kotlin/JVM unless noted)
include(":core:model")        // Engineering data models, units, datums, provenance
include(":core:engineering")  // Shared calculation utilities: datum conversion, trajectory, validation
include(":core:schematic")    // Renderer-agnostic schematic scene graph, projection, hit testing
include(":core:data")         // Repository contracts, JSON codec, synthetic demo fixtures
include(":core:database")     // Android: Room offline persistence
include(":core:ui")           // Android: Compose theme, shared components, schematic canvas renderer

// Functional modules: pure-Kotlin domain logic + Compose feature UI
include(":domain:welldesign")
include(":domain:wellcalc")
include(":domain:ct")
include(":domain:equipment")
include(":domain:workover")
include(":feature:welldesign")
include(":feature:wellcalc")
include(":feature:ctcalc")
include(":feature:equipment")
