package net.serlith.sakurabans.api.data;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.serlith.sakurabans.api.allow.AllowEntry;
import net.serlith.sakurabans.api.appeal.Appeal;
import net.serlith.sakurabans.api.audit.AuditEntry;
import net.serlith.sakurabans.api.model.AddressRecord;
import net.serlith.sakurabans.api.model.NameRecord;
import net.serlith.sakurabans.api.model.PlayerProfileView;
import net.serlith.sakurabans.api.model.Punishment;

/**
 * Everything SakuraBans stores about an account. Implemented by SakuraBans; don't implement it.
 */
public interface PlayerDataExport {

    /**
     * The account.
     *
     * @return the account's UUID
     */
    UUID uuid();

    /**
     * When the export was made.
     *
     * @return the instant
     */
    Instant exportedAt();

    /**
     * The account's profile.
     *
     * @return the profile, or empty if it never joined
     */
    Optional<PlayerProfileView> profile();

    /**
     * The names it used.
     *
     * @return the names
     */
    List<NameRecord> names();

    /**
     * The addresses it joined from.
     *
     * @return the addresses
     */
    List<AddressRecord> addresses();

    /**
     * Punishments it received (player and composite victims).
     *
     * @return the punishments, newest first
     */
    List<Punishment> received();

    /**
     * Punishments it issued as staff.
     *
     * @return the punishments, newest first
     */
    List<Punishment> issued();

    /**
     * Its allow-list entries.
     *
     * @return the entries
     */
    List<AllowEntry> allowEntries();

    /**
     * Its appeals.
     *
     * @return the appeals
     */
    List<Appeal> appeals();

    /**
     * Audit entries about it or by it.
     *
     * @return the entries, newest first
     */
    List<AuditEntry> auditEntries();
}
