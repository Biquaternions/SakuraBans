plugins {
    id("sakurabans.library-conventions")
}

dependencies {
    api(project(":api"))
    implementation(libs.jooq)
}
