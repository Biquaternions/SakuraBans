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
        // packetevents is published only on CodeMC, not on Maven Central.
        maven("https://repo.codemc.io/repository/maven-releases/") {
            name = "codemc"
            content {
                includeGroup("com.github.retrooper")
            }
        }
        // PlaceholderAPI is published only on its own repository.
        maven("https://repo.extendedclip.com/releases/") {
            name = "extendedclip"
            content {
                includeGroup("me.clip")
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
