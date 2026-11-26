package net.serlith.sakurabans.api.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A punishment, active or not. Instances are snapshots: they don't change when the punishment is
 * edited or revoked later. Implemented by SakuraBans; don't implement it.
 */
public interface Punishment {

    /**
     * The database id.
     *
     * @return the id, at least 1
     */
    long id();

    /**
     * The short code shown to players instead of the id.
     *
     * @return the display code
     */
    String displayId();

    /**
     * The kind of punishment, which decides how it is enforced.
     *
     * @return the type
     */
    PunishmentType type();

    /**
     * Whether this is a shadow mute: the muted player's messages are shown to themselves only.
     * Always {@code false} for other types.
     *
     * @return {@code true} for a shadow mute
     */
    boolean shadow();

    /**
     * Who it applies to.
     *
     * @return the victim
     */
    Victim victim();

    /**
     * Who issued it.
     *
     * @return the operator
     */
    Operator operator();

    /**
     * Through which surface it was issued (see {@link Actor#source()}).
     *
     * @return the source
     */
    String source();

    /**
     * Where it applies.
     *
     * @return the scope
     */
    Scope scope();

    /**
     * The reason, as plain text. It is never parsed as MiniMessage, and it may be empty.
     *
     * @return the reason, at most {@link Lengths#MAX_REASON} characters
     */
    String reason();

    /**
     * When it was issued.
     *
     * @return the creation instant
     */
    Instant createdAt();

    /**
     * When it expires. Always empty for a {@link PunishmentType#KICK}.
     *
     * @return the expiry, after {@link #createdAt()}, or empty if it is permanent
     */
    Optional<Instant> expiresAt();

    /**
     * The server it was issued on.
     *
     * @return the server name, or empty if unknown
     */
    Optional<String> originServer();

    /**
     * Who was told about it.
     *
     * @return the visibility
     */
    Visibility visibility();

    /**
     * The template it came from.
     *
     * @return the template, or empty
     */
    Optional<TemplateRef> template();

    /**
     * How it ended. Always empty for a kick.
     *
     * @return the end, or empty if it hasn't ended
     */
    Optional<PunishmentEnd> end();

    /**
     * Whether, when and by whom it was voided. A voided punishment stays in the history but
     * doesn't count for ladders, warning thresholds or statistics; an active one ends with
     * {@link EndKind#VOIDED}.
     *
     * @return the voiding, or empty
     */
    Optional<PunishmentVoid> voiding();

    /**
     * The evidence attached to it.
     *
     * @return the evidence, oldest first
     */
    List<Evidence> evidence();

    /**
     * The version, which goes up by one with every change (edit, end, void, evidence). Send it
     * back as the expected version to make sure you change what you saw.
     *
     * @return the version, at least 1
     */
    long version();

    /**
     * Whether it never expires on its own. A kick counts as permanent.
     *
     * @return {@code true} if {@link #expiresAt()} is empty
     */
    default boolean permanent() {
        return this.expiresAt().isEmpty();
    }

    /**
     * Whether it is in force at the given instant: not a kick, not ended, and not expired.
     *
     * @param now the instant to check
     * @return {@code true} if active at {@code now}
     */
    default boolean isActiveAt(final Instant now) {
        Objects.requireNonNull(now, "now");
        return !this.type().instant()
                && this.end().isEmpty()
                && this.expiresAt().map(expiry -> expiry.isAfter(now)).orElse(true);
    }
}
