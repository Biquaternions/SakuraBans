package net.serlith.sakurabans.core.storage.repository;

import com.google.common.base.Preconditions;
import java.net.InetAddress;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.serlith.sakurabans.api.model.Victim;
import net.serlith.sakurabans.core.storage.StorageService;
import net.serlith.sakurabans.core.storage.dbo.SakuraTables;
import net.serlith.sakurabans.core.storage.dbo.VictimKind;
import net.serlith.sakurabans.core.storage.dbo.records.SbVictimsRecord;
import net.serlith.sakurabans.core.storage.dbo.tables.SbVictims;
import net.serlith.sakurabans.core.util.AddressHashes;
import net.serlith.sakurabans.core.util.InetAddresses;
import org.jooq.DSLContext;
import org.jooq.Record2;

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
        final StoredVictim stored = StoredVictim.of(victim);
        final SbVictims victims = SakuraTables.VICTIMS;
        final Record2<Long, byte[]> existing = dsl.select(victims.ID, victims.ADDRESS)
                .from(victims).where(stored.matches(victims)).fetchOne();
        if (existing != null) {
            if (existing.value2() == null && stored.address() != null) {
                // Naming the address again brings an erased row back.
                dsl.update(victims).set(victims.ADDRESS, stored.address())
                        .where(victims.ID.eq(existing.value1()))
                        .and(victims.ADDRESS.isNull())
                        .execute();
            }
            return existing.value1();
        }
        Preconditions.checkArgument(stored.address() != null,
                "an erased victim that doesn't exist can't be created: %s", victim);
        dsl.insertInto(victims)
                .set(victims.KIND, stored.kind())
                .set(victims.UUID, stored.uuid())
                .set(victims.ADDRESS, stored.address())
                .set(victims.PREFIX_LENGTH, stored.prefixLength())
                .set(victims.ADDRESS_HASH, stored.addressHash())
                .onConflictDoNothing()
                .execute();
        return lookup(dsl, victim).orElseThrow(
                () -> new IllegalStateException("the victim row vanished: " + victim));
    }

    // Erased rows whose address was seen again at a login get it back.
    static int restore(final DSLContext dsl, final VictimKind kind, final UUID uuid,
            final InetAddress address) {
        final SbVictims victims = SakuraTables.VICTIMS;
        return dsl.update(victims)
                .set(victims.ADDRESS, InetAddresses.toBytes(InetAddresses.normalize(address)))
                .where(victims.KIND.eq(kind))
                .and(victims.UUID.eq(uuid))
                .and(victims.ADDRESS_HASH.eq(AddressHashes.of(address)))
                .and(victims.ADDRESS.isNull())
                .execute();
    }

    static Optional<Long> lookup(final DSLContext dsl, final Victim victim) {
        return dsl.select(SakuraTables.VICTIMS.ID)
                .from(SakuraTables.VICTIMS)
                .where(StoredVictim.of(victim).matches(SakuraTables.VICTIMS))
                .fetchOptional(SakuraTables.VICTIMS.ID);
    }

    static Victim victim(final SbVictimsRecord row) {
        return new StoredVictim(row.kind(), row.uuid(), row.address(), row.prefixLength(),
                row.addressHash()).victim();
    }
}
