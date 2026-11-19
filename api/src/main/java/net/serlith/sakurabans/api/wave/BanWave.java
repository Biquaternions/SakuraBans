package net.serlith.sakurabans.api.wave;

import java.time.Instant;
import java.util.Optional;
import net.serlith.sakurabans.api.admin.Versioned;
import net.serlith.sakurabans.api.model.Actor;

/**
 * A ban wave. Implemented by SakuraBans; don't implement it.
 */
public interface BanWave extends Versioned {

    /**
     * The wave's id.
     *
     * @return the id
     */
    long id();

    /**
     * The wave's name, shown to staff.
     *
     * @return the name
     */
    String name();

    /**
     * Where the wave is in its life.
     *
     * @return the status
     */
    Status status();

    /**
     * When it releases by itself.
     *
     * @return the scheduled time, or empty if it is released manually
     */
    Optional<Instant> scheduledAt();

    /**
     * Who created it.
     *
     * @return the actor
     */
    Actor createdBy();

    /**
     * When it was created.
     *
     * @return the instant
     */
    Instant createdAt();

    /**
     * When it was released.
     *
     * @return the instant, or empty if it hasn't been
     */
    Optional<Instant> releasedAt();

    /**
     * How many entries it has.
     *
     * @return the number of entries
     */
    int entryCount();

    /**
     * Where a wave is in its life.
     */
    enum Status {

        /** Collecting entries; released manually. */
        DRAFT,

        /** Collecting entries; released at {@link BanWave#scheduledAt()}. */
        SCHEDULED,

        /** Being released: its entries are being punished, by one instance. */
        RELEASING,

        /** Released; every entry has a result. */
        RELEASED,

        /** Cancelled; nothing was punished. */
        CANCELLED
    }
}
