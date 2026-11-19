package net.serlith.sakurabans.api.allow;

import java.time.Instant;
import java.util.Optional;
import net.serlith.sakurabans.api.model.Actor;

/**
 * An allow-list entry: an {@link AllowedPlayer account} or an {@link AllowedAddress address}.
 * Implemented by SakuraBans; don't implement it.
 */
public sealed interface AllowEntry permits AllowedPlayer, AllowedAddress {

    /**
     * Who added it, and through which surface.
     *
     * @return the actor
     */
    Actor addedBy();

    /**
     * When it was added.
     *
     * @return the instant
     */
    Instant addedAt();

    /**
     * A note about why it is allowed.
     *
     * @return the note, or empty
     */
    Optional<String> note();
}
