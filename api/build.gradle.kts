plugins {
    id("sakurabans.library-conventions")
}

dependencies {
    compileOnly(platform(libs.adventure.bom))
    compileOnly(libs.adventure.api)
}
