plugins {
    id("sakurabans.library-conventions")
}

dependencies {
    api(project(":api"))
    implementation(libs.jooq)
    implementation(libs.hikari)
    implementation(libs.caffeine)
    implementation(libs.jshepherd.core)
    implementation(libs.jshepherd.yaml)
    implementation(libs.cloud.core)
    implementation(libs.cloud.minecraft.extras) {
        // Declares Adventure 4.15; Paper 26.3 and Velocity 4.3 provide Adventure 5.2 (D18).
        exclude(group = "net.kyori")
    }
    implementation(libs.h2)
    implementation(libs.mariadb)
    implementation(libs.mysql)
    implementation(libs.postgresql)

    compileOnly(platform(libs.adventure.bom))
    compileOnly(libs.adventure.api)
    compileOnly(libs.adventure.text.logger.slf4j)
    compileOnly(libs.slf4j.api)

    testImplementation(platform(libs.adventure.bom))
    testImplementation(libs.adventure.api)
    testImplementation(libs.adventure.text.logger.slf4j)
    testImplementation(libs.slf4j.api)
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
