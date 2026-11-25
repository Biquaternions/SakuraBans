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
     * Erases an account's personal data, keeping every punishment in force and every UUID.
     *
     * <p>Kept: every punishment naming the account, ended or active, and every UUID (victims,
     * operators, revokers, voiders, audit actors and subjects, the allow-list). Audit entries are
     * kept.
     *
     * <p>Removed or redacted:
     * <ul>
     *   <li>the profile, the name history and the address history;</li>
     *   <li>name snapshots of the account as staff (operator, revoker, voider, evidence and
     *       allow-list adder, lockdown starter, audit actor) become the text {@code erased};</li>
     *   <li>evidence of punishments naming the account;</li>
     *   <li>allow-list entries for one of its addresses;</li>
     *   <li>its addresses inside audit values become the text {@code erased};</li>
     *   <li>the address of its composite victims, and of address victims on one of its known
     *       addresses that no other known account used, is cleared; only its SHA-256 hash stays.
     *       Their punishments then name an
     *       {@link net.serlith.sakurabans.api.model.ErasedAddressVictim} and still apply to
     *       connections from that exact address. Address victims shared with other accounts,
     *       other accounts' composite victims and range victims are untouched.</li>
     * </ul>
     *
     * <p>An erased victim gets its address back when the address is seen again: at a login
     * stopped by its punishment, or when a punishment names the address (or the account and
     * address) again.
     *
     * <p>Not handled: free text (reasons, notes), names inside audit values, export files, server
     * logs and webhooks already sent. A plain SHA-256 hash of an IPv4 address can be reversed by
     * trying every address; the point is that the address isn't stored.
     *
     * @param uuid the account
     * @param actor who requests the erase
     * @return what was removed or changed
     */
    CompletableFuture<AdminResult<EraseResult>> erase(UUID uuid, Actor actor);
}
