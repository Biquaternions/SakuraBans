package net.serlith.sakurabans.core.util;

/**
 * Values fixed at build time. Generated from {@code core/src/template/java}; edit the template.
 */
public final class BuildConstants {

    /** The plugin version with its release channel: {@code 1.0.0-alpha.3}. */
    public static final String VERSION = "${version}";

    /** The release channel's name, see {@link ReleaseChannel}. */
    public static final String CHANNEL = "${channel}";

    /** The build number within the channel, or empty when the build has none. */
    public static final String BUILD = "${buildNumber}";

    /** The abbreviated git commit the jar was built from, or {@code unknown}. */
    public static final String GIT_COMMIT = "${commit}";

    private BuildConstants() {
    }
}
