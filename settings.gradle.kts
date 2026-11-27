pluginManagement {
    includeBuild("build-logic")
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/") {
            name = "papermc"
        }
        // packetevents (S61) is published only on CodeMC, not on Maven Central.
        maven("https://repo.codemc.io/repository/maven-releases/") {
            name = "codemc"
            content {
                includeGroup("com.github.retrooper")
            }
        }
    }
}

rootProject.name = "SakuraBans"

include(
    "api",
    "core",
    "platforms:paper",
    "platforms:velocity",
)
