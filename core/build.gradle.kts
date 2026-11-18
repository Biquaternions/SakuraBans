

group = "net.serlith.sakurabans"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":api"))
    implementation("org.jooq:jooq:3.21.9")
}
