package net.serlith.sakurabans.api.settings;

/**
 * Where an effective value comes from.
 */
public enum ValueOrigin {

    /** SakuraBans' built-in default (the key is missing from the file). */
    DEFAULT,

    /** The local file ({@code config.yml} or {@code lang/<locale>.yml}). */
    FILE,

    /** An override stored in the database, shared by every instance. */
    OVERRIDE
}
