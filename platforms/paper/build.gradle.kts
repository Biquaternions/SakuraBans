plugins {
    id("sakurabans.platform-conventions")
    alias(libs.plugins.run.paper)
}

dependencies {
    implementation(project(":core"))
    compileOnly(libs.paper.api)
}

tasks {
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
