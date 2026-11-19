package net.serlith.sakurabans.core.storage.repository;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.serlith.sakurabans.api.model.Victim;
import net.serlith.sakurabans.core.storage.StorageService;
import net.serlith.sakurabans.core.storage.dbo.SakuraTables;
import net.serlith.sakurabans.core.storage.dbo.records.SbVictimsRecord;
import org.jooq.DSLContext;

public final class VictimRepository {

    private final StorageService storage;

    public VictimRepository(final StorageService storage) {
        this.storage = storage;
    }

    public CompletableFuture<Long> id(final Victim victim) {
        return this.storage.writeRetryingConflicts(
                configuration -> resolve(configuration.dsl(), victim));
    }

    public CompletableFuture<Optional<Long>> find(final Victim victim) {
        return this.storage.read(dsl -> lookup(dsl, victim));
    }

    public CompletableFuture<Optional<Victim>> byId(final long id) {
        return this.storage.read(dsl -> dsl.selectFrom(SakuraTables.VICTIMS)
                .where(SakuraTables.VICTIMS.ID.eq(id))
                .fetchOptional()
                .map(VictimRepository::victim));
    }

    static long resolve(final DSLContext dsl, final Victim victim) {
        final Optional<Long> existing = lookup(dsl, victim);
        if (existing.isPresent()) {
            return existing.get();
        }
        final StoredVictim stored = StoredVictim.of(victim);
        dsl.insertInto(SakuraTables.VICTIMS)
                .set(SakuraTables.VICTIMS.KIND, stored.kind())
                .set(SakuraTables.VICTIMS.UUID, stored.uuid())
                .set(SakuraTables.VICTIMS.ADDRESS, stored.address())
                .set(SakuraTables.VICTIMS.PREFIX_LENGTH, stored.prefixLength())
                .onConflictDoNothing()
                .execute();
        return lookup(dsl, victim).orElseThrow(
                () -> new IllegalStateException("the victim row vanished: " + victim));
    }

    static Optional<Long> lookup(final DSLContext dsl, final Victim victim) {
        return dsl.select(SakuraTables.VICTIMS.ID)
                .from(SakuraTables.VICTIMS)
                .where(StoredVictim.of(victim).matches(SakuraTables.VICTIMS))
                .fetchOptional(SakuraTables.VICTIMS.ID);
    }

    static Victim victim(final SbVictimsRecord row) {
        return new StoredVictim(row.kind(), row.uuid(), row.address(), row.prefixLength())
                .victim();
    }
}
