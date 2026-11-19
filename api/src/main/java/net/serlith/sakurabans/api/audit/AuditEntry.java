package net.serlith.sakurabans.api.audit;

import java.time.Instant;
import java.util.Optional;
import net.serlith.sakurabans.api.model.Actor;

/**
 * One entry of the audit log. Implemented by SakuraBans; don't implement it.
 */
public interface AuditEntry {

    /**
     * The entry's id.
     *
     * @return the id, increasing with time
     */
    long id();

    /**
     * When it happened.
     *
     * @return the instant
     */
    Instant at();

    /**
     * Who did it, and through which surface.
     *
     * @return the actor
     */
    Actor actor();

    /**
     * What was done.
     *
     * @return the action
     */
    AuditAction action();

    /**
     * What it is about.
     *
     * @return the subject type
     */
    SubjectType subjectType();

    /**
     * Which entity, as described on {@link SubjectType}.
     *
     * @return the subject id
     */
    String subjectId();

    /**
     * The value before the change, as text (JSON for structured values).
     *
     * @return the old value, or empty
     */
    Optional<String> oldValue();

    /**
     * The value after the change, as text (JSON for structured values).
     *
     * @return the new value, or empty
     */
    Optional<String> newValue();
}
