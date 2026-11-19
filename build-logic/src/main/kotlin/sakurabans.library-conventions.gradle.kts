plugins {
    id("sakurabans.java-conventions")
    `java-library`
}

java {
    withJavadocJar()
    withSourcesJar()
}

tasks.withType<Javadoc>().configureEach {
    // Checkstyle already requires Javadoc where it matters; doclint checks the rest.
    (options as StandardJavadocDocletOptions).addBooleanOption("Xdoclint:all,-missing", true)
}
