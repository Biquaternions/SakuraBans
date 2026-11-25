plugins {
    id("sakurabans.platform-conventions")
    alias(libs.plugins.run.paper)
}

dependencies {
    implementation(project(":core"))
    implementation(libs.cloud.paper)
    compileOnly(libs.paper.api)
}

val shadedOnPaper = setOf("com.google.guava", "com.google.code.gson")

val generatePaperLibraries = tasks.register<GeneratePaperLibraries>("generatePaperLibraries") {
    libraries = configurations.runtimeClasspath
        .flatMap { it.incoming.resolutionResult.rootComponent }
        .map { root ->
            GeneratePaperLibraries.directLibraries(root)
                .filterNot { it.substringBefore(':') in shadedOnPaper }
        }
    outputDirectory = layout.buildDirectory.dir("generated/resources/paperLibraries")
}

sourceSets.main {
    resources.srcDir(generatePaperLibraries)
}

val ownGroup = project.group.toString()

tasks {
    shadowJar {
        // Our own classes, Guava and Gson; every other library is resolved by SakuraBansPaperLoader.
        dependencies {
            exclude { it.moduleGroup != ownGroup && it.moduleGroup !in shadedOnPaper }
        }
        val libs = "net.serlith.sakurabans.libs"
        relocate("com.google.common", "$libs.guava")
        relocate("com.google.thirdparty", "$libs.guava.thirdparty")
        relocate("com.google.gson", "$libs.gson")
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
