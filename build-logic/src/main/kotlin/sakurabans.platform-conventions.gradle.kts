import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    id("sakurabans.java-conventions")
    id("com.gradleup.shadow")
}

// The jar a server owner installs: SakuraBans-Paper-1.0.0-alpha.3.jar.
base {
    archivesName = "SakuraBans-" + project.name.replaceFirstChar { it.uppercase() }
}

tasks.named<Jar>("jar") {
    archiveClassifier = "thin"
}

tasks.named<ShadowJar>("shadowJar") {
    archiveClassifier = ""
}

tasks.named("build") {
    dependsOn(tasks.named("shadowJar"))
}
