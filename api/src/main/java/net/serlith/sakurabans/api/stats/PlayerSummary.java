package net.serlith.sakurabans.api.stats;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.serlith.sakurabans.api.model.Punishment;

/**
 * An account's punishment record at a glance, for profile views. Implemented by SakuraBans;
 * don't implement it.
 */
public interface PlayerSummary {

    /**
     * The account.
     *
     * @return the account's UUID
     */
    UUID uuid();

    /**
     * Its active punishments, in every scope, applied as the login check applies them.
     *
     * @return the punishments, newest first
     */
    List<Punishment> active();

    /**
     * How many punishments it has received, per kind, voided ones excluded.
     *
     * @return the counts by kind id
     */
    Map<String, Long> counts();

    /**
     * How many of its punishments were voided.
     *
     * @return the count
     */
    long voided();

    /**
     * Its most recent punishment, voided ones excluded.
     *
     * @return the punishment, or empty
     */
    Optional<Punishment> last();
}
