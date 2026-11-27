plugins {
    java
    checkstyle
    id("io.freefair.lombok")
}

val libs = versionCatalogs.named("libs")

fun lib(alias: String) = libs.findLibrary(alias).get()

version = ReleaseVersion.of(project).full

java {
    toolchain.languageVersion = JavaLanguageVersion.of(25)
}

lombok {
    version = libs.findVersion("lombok").get().requiredVersion
}

dependencies {
    compileOnly(lib("jspecify"))
    testCompileOnly(lib("jspecify"))

    testImplementation(platform(lib("junit-bom")))
    testImplementation(lib("junit-jupiter"))
    testImplementation(lib("assertj"))
    testRuntimeOnly(lib("junit-platform-launcher"))
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = 25
    options.compilerArgs.addAll(listOf("-parameters", "-Xlint:all,-processing,-serial,-classfile"))
}

tasks.withType<Javadoc>().configureEach {
    options.encoding = "UTF-8"
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

checkstyle {
    toolVersion = libs.findVersion("checkstyle").get().requiredVersion
    configDirectory = layout.settingsDirectory.dir("config/checkstyle")
    maxWarnings = 0
}
