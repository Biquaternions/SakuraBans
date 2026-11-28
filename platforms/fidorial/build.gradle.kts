plugins {
    id("sakurabans.platform-conventions")
}

dependencies {
    implementation(project(":core"))
    compileOnly(libs.fidorial.api)
    testImplementation(libs.fidorial.api)
}

val shadedOnFidorial = setOf("com.google.guava", "com.google.code.gson")
val ownGroup = project.group.toString()

tasks {
    shadowJar {
        dependencies {
            exclude { it.moduleGroup != ownGroup && it.moduleGroup !in shadedOnFidorial }
        }
        val libs = "net.serlith.sakurabans.libs"
        relocate("com.google.common", "$libs.guava")
        relocate("com.google.thirdparty", "$libs.guava.thirdparty")
        relocate("com.google.gson", "$libs.gson")
    }

    processResources {
        val props = mapOf("version" to project.version)
        inputs.properties(props)
        filesMatching("paper-plugin.yml") {
            expand(props)
        }
    }
}