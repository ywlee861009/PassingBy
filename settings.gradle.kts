pluginManagement {
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

rootProject.name = "PassingBy"
include(
    ":app",
    ":journey:domain",
    ":journey:data",
    ":experience:api",
    ":experience:train",
    ":experience:voyage",
    ":experience:airplane",
    ":experience:ship",
    ":experience:spaceship",
    ":feature:journey",
    ":platform:notifications",
    ":platform:billing",
    ":platform:telemetry",
)
