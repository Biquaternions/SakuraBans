plugins {
    id("sakurabans.platform-conventions")
    alias(libs.plugins.run.velocity)
}

dependencies {
    implementation(project(":core"))
    implementation(libs.cloud.velocity)
    compileOnly(libs.velocity.api)
    annotationProcessor(libs.velocity.api)
}

tasks {
    shadowJar {
        // Shadow 9 drops duplicate files before transformers see them unless they are included.
        filesMatching("META-INF/services/**") {
            duplicatesStrategy = DuplicatesStrategy.INCLUDE
        }
        mergeServiceFiles()

        // Provided by Velocity 4.3 (plan 3.4): never shade them.
        dependencies {
            exclude(dependency("net.kyori:.*"))
            exclude(dependency("org.slf4j:.*"))
            exclude(dependency("com.google.inject:.*"))
            exclude(dependency("jakarta.inject:.*"))
            exclude(dependency("org.jspecify:.*"))
            // Annotation-only artifacts pulled in by JShepherd, MySQL and Caffeine.
            exclude(dependency("com.google.auto.service:.*"))
            exclude(dependency("com.google.errorprone:.*"))
            exclude(dependency("org.checkerframework:.*"))
        }

        val libs = "net.serlith.sakurabans.libs"
        relocate("org.jooq", "$libs.jooq")
        relocate("io.r2dbc", "$libs.r2dbc")
        relocate("org.reactivestreams", "$libs.reactivestreams")
        relocate("com.zaxxer.hikari", "$libs.hikari")
        relocate("com.github.benmanes.caffeine", "$libs.caffeine")
        relocate("de.bsommerfeld.jshepherd", "$libs.jshepherd")
        relocate("org.yaml.snakeyaml", "$libs.snakeyaml")
        relocate("org.incendo.cloud", "$libs.cloud")
        relocate("io.leangen.geantyref", "$libs.geantyref")
        relocate("org.h2", "$libs.h2")
        relocate("org.mariadb.jdbc", "$libs.mariadb")
        relocate("com.mysql", "$libs.mysql")
        relocate("com.google.protobuf", "$libs.protobuf")
        relocate("org.postgresql", "$libs.postgresql")
    }

    runVelocity {
        velocityVersion(libs.versions.velocity.get())
        jvmArgs("-Dsakurabans.selfCheck=true")
    }
}
