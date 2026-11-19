package net.serlith.sakurabans.api.admin;

/**
 * An editable entity with a version for optimistic concurrency: the version goes up by one with
 * every change, and updates carry the version the caller last saw.
 */
public interface Versioned {

    /**
     * The current version.
     *
     * @return the version, at least 1
     */
    long version();
}
