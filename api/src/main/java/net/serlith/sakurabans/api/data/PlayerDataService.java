package net.serlith.sakurabans.api.data;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.serlith.sakurabans.api.admin.AdminResult;
import net.serlith.sakurabans.api.model.Actor;

/**
 * Data subject requests. Both operations are audited. Every method returns a future completed on
 * a worker thread. Implemented by SakuraBans; don't implement it.
 */
public interface PlayerDataService {

    /**
     * Collects everything SakuraBans stores about an account.
     *
     * @param uuid the account
     * @param actor who requests the export
     * @return the export
     */
    CompletableFuture<AdminResult<PlayerDataExport>> export(UUID uuid, Actor actor);

    /**
     * Erases an account's personal data. The profile, names, addresses, allow-list entries,
     * appeals and evidence links are deleted, and its ended punishments are expunged. Its active
     * punishments stay in force but lose their personal data (name snapshots, and the address of
     * composite victims, so no IP address of the account remains). Punishments it issued keep
     * their record with an "erased" operator. Address-only and range punishments aren't tied to
     * the account and are kept.
     *
     * @param uuid the account
     * @param actor who requests the erase
     * @return what was removed or changed
     */
    CompletableFuture<AdminResult<EraseResult>> erase(UUID uuid, Actor actor);
}
