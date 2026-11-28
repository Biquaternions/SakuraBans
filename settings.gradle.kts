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
        maven("https://repo.euphyllia.moe/repository/maven-public/") {
            name = "euphyllia"
            content {
                includeGroup("fr.fidorial")
            }
        }
        maven("https://repo.opencollab.dev/maven-releases") {
            name = "opencollab-releases"
            content {
                includeGroup("org.spongepowered")
            }
        }
        maven("https://repo.opencollab.dev/maven-snapshots") {
            name = "opencollab-snapshots"
            content {
                includeGroup("org.spongepowered")
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
    "platforms:fidorial",
)
