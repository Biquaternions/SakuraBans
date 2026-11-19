plugins {
    id("sakurabans.platform-conventions")
    alias(libs.plugins.run.paper)
}

dependencies {
    implementation(project(":core"))
    implementation(libs.cloud.paper)
    compileOnly(libs.paper.api)
}

val generatePaperLibraries = tasks.register<GeneratePaperLibraries>("generatePaperLibraries") {
    libraries = configurations.runtimeClasspath
        .flatMap { it.incoming.resolutionResult.rootComponent }
        .map(GeneratePaperLibraries::directLibraries)
    outputDirectory = layout.buildDirectory.dir("generated/resources/paperLibraries")
}

sourceSets.main {
    resources.srcDir(generatePaperLibraries)
}

val ownGroup = project.group.toString()

tasks {
    shadowJar {
        // Only our own classes; every library is resolved by SakuraBansPaperLoader (plan 3.3).
        dependencies {
            exclude { it.moduleGroup != ownGroup }
        }
    }

    runServer {
        minecraftVersion("26.3")
        jvmArgs("-Xms2G", "-Xmx2G", "-Dcom.mojang.eula.agree=true")
    }

    processResources {
        val props = mapOf("version" to project.version)
        inputs.properties(props)
        filesMatching("paper-plugin.yml") {
            expand(props)
        }
    }
}
