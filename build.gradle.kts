plugins {
    id("java")
    id("io.freefair.lombok") version "9.8.2"
    id("com.gradleup.shadow") version "9.6.1"
}

group = "net.serlith"
version = "1.0-SNAPSHOT"

allprojects {
    apply {
        plugin("java")
        plugin("io.freefair.lombok")
        plugin("com.gradleup.shadow")
    }

    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/") {
            name = "papermc"
        }
    }
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:6.0.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}