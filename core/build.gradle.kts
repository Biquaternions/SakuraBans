plugins {
    id("sakurabans.library-conventions")
    `java-test-fixtures`
}

dependencies {
    api(project(":api"))
    implementation(libs.jooq)
    implementation(libs.hikari)
    implementation(libs.caffeine)
    implementation(libs.configurate.yaml)
    implementation(libs.cloud.core)
    implementation(libs.cloud.minecraft.extras) {
        // Declares Adventure 4.15; Paper 26.3 and Velocity 4.3 provide Adventure 5.2 (D18).
        exclude(group = "net.kyori")
    }
    implementation(libs.h2)
    implementation(libs.mariadb)
    implementation(libs.mysql)
    implementation(libs.postgresql)

    // Provided by Paper and Velocity (plan 3.2), so never shaded or downloaded.
    implementation(libs.guava)

    compileOnly(platform(libs.adventure.bom))
    compileOnly(libs.adventure.api)
    compileOnly(libs.adventure.text.minimessage)
    compileOnly(libs.adventure.text.serializer.plain)
    compileOnly(libs.adventure.text.logger.slf4j)
    compileOnly(libs.slf4j.api)

    testFixturesCompileOnly(libs.jspecify)
    testFixturesCompileOnly(platform(libs.adventure.bom))
    testFixturesCompileOnly(libs.adventure.api)

    testImplementation(platform(libs.adventure.bom))
    testImplementation(libs.adventure.api)
    testImplementation(libs.adventure.text.logger.slf4j)
    testImplementation(libs.adventure.text.minimessage)
    testImplementation(libs.adventure.text.serializer.plain)
    testImplementation(libs.slf4j.api)
}

val integrationTest: SourceSet by sourceSets.creating {
    compileClasspath += sourceSets.main.get().output + sourceSets.test.get().output
    runtimeClasspath += sourceSets.main.get().output + sourceSets.test.get().output
}

configurations[integrationTest.implementationConfigurationName]
    .extendsFrom(configurations.testImplementation.get())
configurations[integrationTest.compileOnlyConfigurationName]
    .extendsFrom(configurations.testCompileOnly.get())
configurations[integrationTest.runtimeOnlyConfigurationName]
    .extendsFrom(configurations.testRuntimeOnly.get())

dependencies {
    "integrationTestImplementation"(testFixtures(project))
    "integrationTestImplementation"(platform(libs.testcontainers.bom))
    "integrationTestImplementation"(libs.testcontainers.junit.jupiter)
    "integrationTestImplementation"(libs.testcontainers.mariadb)
    "integrationTestImplementation"(libs.testcontainers.mysql)
    "integrationTestImplementation"(libs.testcontainers.postgresql)
}

val integrationTestTask = tasks.register<Test>("integrationTest") {
    description = "Runs the storage tests on MariaDB, MySQL and PostgreSQL (needs Docker)."
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    testClassesDirs = integrationTest.output.classesDirs
    classpath = integrationTest.runtimeClasspath
    shouldRunAfter(tasks.test)
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
