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

    /** Server names, category names, template names and kind ids. */
    public static final int MAX_IDENTIFIER = 64;

    /** Source tags of actions ({@code in-game}, {@code hydra-panel}, {@code api:<plugin>}). */
    public static final int MAX_SOURCE = 64;

    /** Evidence references (URLs or {@code <namespace>:<type>:<id>}). */
    public static final int MAX_EVIDENCE = 512;

    private Lengths() {
    }
}
