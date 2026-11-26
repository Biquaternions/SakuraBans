package net.serlith.sakurabans.api.data;

import java.time.Instant;
import java.util.Optional;
import net.serlith.sakurabans.api.model.Actor;

/**
 * One audit log entry in a {@link PlayerDataExport}. Implemented by SakuraBans; don't implement
 * it.
 */
public interface AuditRecord {

    /**
     * When it happened.
     *
     * @return the instant
     */
    Instant at();

    /**
     * Who did it.
     *
     * @return the actor
     */
    Actor actor();

    /**
     * What was done.
     *
     * @return the action, for example {@code CREATE} or {@code ERASE}
     */
    String action();

    /**
     * What it was done to.
     *
     * @return the subject, for example {@code PUNISHMENT 42} or {@code PLAYER <uuid>}
     */
    String subject();

    /**
     * The value before, as text.
     *
     * @return the value, or empty
     */
    Optional<String> oldValue();

    /**
     * The value after, as text.
     *
     * @return the value, or empty
     */
    Optional<String> newValue();
}
