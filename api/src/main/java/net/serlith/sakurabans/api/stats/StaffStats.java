package net.serlith.sakurabans.api.stats;

import java.time.Instant;
import java.util.Map;
import net.serlith.sakurabans.api.model.Operator;

/**
 * A staff member's activity in a time range. Implemented by SakuraBans; don't implement it.
 */
public interface StaffStats {

    /**
     * The staff member.
     *
     * @return the operator
     */
    Operator operator();

    /**
     * The start of the range (inclusive).
     *
     * @return the start
     */
    Instant from();

    /**
     * The end of the range (exclusive).
     *
     * @return the end
     */
    Instant to();

    /**
     * Punishments they issued, per kind, voided ones excluded.
     *
     * @return the counts by kind id
     */
    Map<String, Long> issued();

    /**
     * Punishments they revoked, per kind.
     *
     * @return the counts by kind id
     */
    Map<String, Long> revoked();

    /**
     * Punishments they issued that were later voided, per kind.
     *
     * @return the counts by kind id
     */
    Map<String, Long> voided();
}
