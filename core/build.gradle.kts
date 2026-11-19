plugins {
    id("sakurabans.library-conventions")
}

dependencies {
    api(project(":api"))
    implementation(libs.jooq)

    compileOnly(platform(libs.adventure.bom))
    compileOnly(libs.adventure.api)
    compileOnly(libs.adventure.text.logger.slf4j)
    compileOnly(libs.slf4j.api)
}

val gitCommit = providers.exec {
    commandLine("git", "rev-parse", "--short=12", "HEAD")
    isIgnoreExitValue = true
}.standardOutput.asText.map { it.trim().ifEmpty { "unknown" } }

val generateBuildConstants = tasks.register<Sync>("generateBuildConstants") {
    val properties = mapOf(
        "version" to project.version.toString(),
        "commit" to runCatching { gitCommit.get() }.getOrDefault("unknown"),
    )
    inputs.properties(properties)
    from("src/template/java")
    into(layout.buildDirectory.dir("generated/sources/buildConstants/java"))
    expand(properties)
}

sourceSets.main {
    java.srcDir(generateBuildConstants)
}
