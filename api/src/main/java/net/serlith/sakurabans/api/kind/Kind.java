package net.serlith.sakurabans.api.kind;

import java.util.Optional;
import java.util.Set;
import net.serlith.sakurabans.api.admin.Versioned;
import net.serlith.sakurabans.api.model.AddressStrictness;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.model.Scope;

/**
 * A punishment kind: a named preset on top of a {@link PunishmentType}, for example
 * {@code shadow-mute} (a mute whose messages only the sender sees) or {@code blacklist} (a
 * permanent global ban that reaches every linked account). A punishment copies its kind's
 * modifiers when it is created. Implemented by SakuraBans; don't implement it.
 */
public interface Kind extends Versioned {

    /**
     * The kind's id, used by drafts, commands and queries.
     *
     * @return the id, lower case
     */
    String id();

    /**
     * The name shown to staff.
     *
     * @return the display name
     */
    String displayName();

    /**
     * The type that decides how punishments of this kind are enforced.
     *
     * @return the base type
     */
    PunishmentType baseType();

    /**
     * Whether its mutes are shadow mutes.
     *
     * @return {@code true} for shadow mutes; always {@code false} unless the base type is a mute
     */
    boolean shadow();

    /**
     * How far its punishments reach through shared addresses.
     *
     * @return the strictness, or empty for the configured one
     */
    Optional<AddressStrictness> strictness();

    /**
     * The scope its punishments get when the draft names none.
     *
     * @return the scope, or empty for the configured default scope
     */
    Optional<Scope> defaultScope();

    /**
     * How the victim is treated.
     *
     * @return the address mode
     */
    AddressMode addressMode();

    /**
     * Whether its punishments are always permanent; a duration is then rejected.
     *
     * @return {@code true} if permanent only
     */
    boolean permanentOnly();

    /**
     * Evidence types its punishments must have (captured by an
     * {@link net.serlith.sakurabans.api.integration.EvidenceCapturer} if missing).
     *
     * @return the evidence types, possibly empty
     */
    Set<String> requiredEvidence();

    /**
     * Whether SakuraBans ships this kind. Built-in kinds can be edited but not deleted.
     *
     * @return {@code true} for a built-in kind
     */
    boolean builtIn();
}
