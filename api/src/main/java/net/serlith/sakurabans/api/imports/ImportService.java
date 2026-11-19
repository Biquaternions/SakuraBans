package net.serlith.sakurabans.api.imports;

import java.util.concurrent.CompletableFuture;
import net.serlith.sakurabans.api.admin.AdminResult;
import net.serlith.sakurabans.api.model.Actor;

/**
 * Bulk import of punishments, for migrating from another system. Imported punishments keep their
 * history, are audited as one import, and fire no punishment events. Implemented by SakuraBans;
 * don't implement it.
 */
public interface ImportService {

    /**
     * Imports punishments. The input is read once, in order, on a worker thread, so it may be a
     * lazy stream over the other system's data.
     *
     * @param punishments the punishments to import
     * @param options how the import runs
     * @param actor who runs it
     * @return the report
     */
    CompletableFuture<AdminResult<ImportReport>> importPunishments(
            Iterable<ImportedPunishment> punishments, ImportOptions options, Actor actor);
}
