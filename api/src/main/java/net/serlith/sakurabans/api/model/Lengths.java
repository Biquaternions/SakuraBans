package net.serlith.sakurabans.api.model;

/**
 * Maximum lengths of the text values in the model. They match the database columns.
 */
public final class Lengths {

    /** Reasons of punishments and revocations. */
    public static final int MAX_REASON = 1024;

    /** Minecraft account names, including Floodgate prefixes. */
    public static final int MAX_PLAYER_NAME = 32;

    /** Operator names, which may be any {@code --sender} text. */
    public static final int MAX_OPERATOR_NAME = 64;

    /** Server names, category names and template names. */
    public static final int MAX_IDENTIFIER = 64;

    private Lengths() {
    }
}
