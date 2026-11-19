package net.serlith.sakurabans.api.appeal;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import net.serlith.sakurabans.api.admin.Versioned;
import net.serlith.sakurabans.api.model.Actor;

/**
 * An appeal against a punishment. Implemented by SakuraBans; don't implement it.
 */
public interface Appeal extends Versioned {

    /**
     * The appeal's id.
     *
     * @return the id
     */
    long id();

    /**
     * The punishment appealed against.
     *
     * @return its database id
     */
    long punishmentId();

    /**
     * Who appeals.
     *
     * @return the account's UUID
     */
    UUID appellant();

    /**
     * What the appellant wrote.
     *
     * @return the message, plain text
     */
    String message();

    /**
     * When it was submitted.
     *
     * @return the instant
     */
    Instant createdAt();

    /**
     * Where it stands.
     *
     * @return the status
     */
    Status status();

    /**
     * How it was decided.
     *
     * @return the decision, or empty while open
     */
    Optional<Decision> decision();

    /**
     * Where an appeal stands.
     */
    enum Status {

        /** Waiting for staff. */
        OPEN,

        /** Decided with {@link AppealOutcome#REVOKE} or {@link AppealOutcome#VOID}. */
        ACCEPTED,

        /** Decided with {@link AppealOutcome#REJECT}. */
        REJECTED
    }

    /**
     * A staff decision on an appeal.
     */
    interface Decision {

        /**
         * The outcome.
         *
         * @return the outcome
         */
        AppealOutcome outcome();

        /**
         * Who decided.
         *
         * @return the actor
         */
        Actor actor();

        /**
         * When.
         *
         * @return the instant
         */
        Instant at();

        /**
         * A note for the appellant.
         *
         * @return the note, plain text, or empty
         */
        Optional<String> note();
    }
}
