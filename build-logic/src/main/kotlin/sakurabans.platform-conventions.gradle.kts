plugins {
    id("sakurabans.java-conventions")
    id("com.gradleup.shadow")
}

tasks.named("build") {
    dependsOn(tasks.named("shadowJar"))
}
