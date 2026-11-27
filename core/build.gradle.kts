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
        // Declares Adventure 4.15; Paper 26.3 and Velocity 4.3 provide Adventure 5.2.
        exclude(group = "net.kyori")
    }
    implementation(libs.h2)
    implementation(libs.mariadb)
    implementation(libs.mysql)
    implementation(libs.postgresql)
    implementation(libs.hsqldb)
    // GeoIP: only geoip.MaxMindCountryDatabase names it, and only when geoip.enabled.
    implementation(libs.maxmind.db)

    // Shaded and relocated on every platform: the servers' copies are never used.
    implementation(libs.guava)
    implementation(libs.gson)

    // Optional at runtime: only LuckPermsBridge touches it, after checking it is installed.
    compileOnly(libs.luckperms.api)
    // Optional at runtime: only placeholder.MiniPlaceholdersBridge touches it, after the
    // platform found the plugin enabled. Its one dependency (an injector) isn't needed.
    compileOnly(libs.miniplaceholders.api) {
        isTransitive = false
    }
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
    testImplementation(libs.miniplaceholders.api) {
        isTransitive = false
    }
    // Brigadier as Paper uses it, to test suggestions the way clients get them.
    testImplementation(libs.cloud.brigadier)
    testImplementation(libs.brigadier)
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

// The editor protocol's shared fixtures (protocol/fixtures), read in place by the tests.
val protocolFixtures = rootProject.layout.projectDirectory.dir("protocol/fixtures")
tasks.withType<Test>().configureEach {
    inputs.dir(protocolFixtures).withPathSensitivity(PathSensitivity.RELATIVE)
    systemProperty("sakurabans.protocolFixtures", protocolFixtures.asFile.absolutePath)
    // Opt-in tests against a running editor site (EditorSiteInteropTest): the site's URL, and
    // a second one started with an upload token.
    listOf("editorSite", "editorSiteSecured", "editorSiteUploadToken").forEach { name ->
        providers.gradleProperty(name).orNull?.let { systemProperty("sakurabans.$name", it) }
    }
}

val gitCommit = providers.exec {
    commandLine("git", "rev-parse", "--short=12", "HEAD")
    isIgnoreExitValue = true
}.standardOutput.asText.map { it.trim().ifEmpty { "unknown" } }

val generateBuildConstants = tasks.register<Sync>("generateBuildConstants") {
    val release = ReleaseVersion.of(project)
    val properties = mapOf(
        "version" to release.full,
        "channel" to release.channel,
        "buildNumber" to release.buildNumber,
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
