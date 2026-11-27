package net.serlith.sakurabans.core.util;

import java.util.Locale;

public enum ReleaseChannel {
    DEV,
    ALPHA,
    BETA,
    RC,
    RELEASE;

    /** The channel this jar was built for. */
    public static ReleaseChannel current() {
        return valueOf(BuildConstants.CHANNEL.toUpperCase(Locale.ROOT));
    }

    public boolean stable() {
        return this == RELEASE;
    }

    /**
     * Whether a database of an older build is updated. Before the betas the schema changes
     * without migrations: the database is dropped and created again.
     */
    public boolean migratesSchema() {
        return this.compareTo(BETA) >= 0;
    }

    public String label() {
        return this.name().toLowerCase(Locale.ROOT);
    }
}
