plugins {
    id("sakurabans.platform-conventions")
    alias(libs.plugins.run.velocity)
}

dependencies {
    implementation(project(":core"))
    compileOnly(libs.velocity.api)
    annotationProcessor(libs.velocity.api)
}

tasks {
    runVelocity {
        velocityVersion(libs.versions.velocity.get())
    }
}
