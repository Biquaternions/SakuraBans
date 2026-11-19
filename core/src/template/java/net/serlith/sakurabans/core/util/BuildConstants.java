package net.serlith.sakurabans.core.util;

/**
 * Values fixed at build time. Generated from {@code core/src/template/java}; edit the template.
 */
public final class BuildConstants {

    /** The plugin version, from {@code gradle.properties}. */
    public static final String VERSION = "${version}";

    /** The abbreviated git commit the jar was built from, or {@code unknown}. */
    public static final String GIT_COMMIT = "${commit}";

    private BuildConstants() {
    }
}
