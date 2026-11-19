package net.serlith.sakurabans.api.model;

/**
 * The kinds of punishment.
 */
public enum PunishmentType {

    /** Denies joining. */
    BAN,

    /** Denies chatting and the configured commands. */
    MUTE,

    /** A recorded warning; warnings can trigger threshold actions. */
    WARN,

    /** Disconnects once. A kick is never active, so it never expires and can't be revoked. */
    KICK;

    /**
     * Whether this type takes effect once and is never active.
     *
     * @return {@code true} for {@link #KICK}
     */
    public boolean instant() {
        return this == KICK;
    }

    /**
     * Whether several active punishments of this type may apply to the same victim in the same
     * scope. Otherwise there is at most one.
     *
     * @return {@code true} for {@link #WARN}
     */
    public boolean stacks() {
        return this == WARN;
    }
}
