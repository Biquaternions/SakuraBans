package net.serlith.sakurabans.core.storage.repository;

import java.net.InetAddress;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.serlith.sakurabans.api.model.Actor;
import net.serlith.sakurabans.api.model.Punishment;
import net.serlith.sakurabans.core.model.AuditAction;
import net.serlith.sakurabans.core.model.AuditEntry;
import net.serlith.sakurabans.core.model.SakuraPlayerData;
import net.serlith.sakurabans.core.model.SubjectType;
import net.serlith.sakurabans.core.storage.StorageService;
import net.serlith.sakurabans.core.storage.dbo.OperatorKind;
import net.serlith.sakurabans.core.storage.dbo.SakuraTables;
import net.serlith.sakurabans.core.storage.dbo.VictimKind;
import net.serlith.sakurabans.core.storage.dbo.records.SbVictimsRecord;
import net.serlith.sakurabans.core.storage.dbo.tables.SbAuditLog;
import net.serlith.sakurabans.core.storage.dbo.tables.SbPlayerAddresses;
import net.serlith.sakurabans.core.storage.dbo.tables.SbPunishments;
import net.serlith.sakurabans.core.storage.dbo.tables.SbVictims;
import net.serlith.sakurabans.core.util.AddressHashes;
import net.serlith.sakurabans.core.util.InetAddresses;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Table;
import org.jooq.impl.DSL;

public final class PlayerDataRepository {

    public static final String ERASED = "erased";

    private final StorageService storage;
    private final PunishmentMapper mapper;

    public PlayerDataRepository(final StorageService storage, final PunishmentMapper mapper) {
        this.storage = storage;
        this.mapper = mapper;
    }

    public record Export(List<Punishment> received, List<Punishment> issued,
            List<AuditEntry> audit) {

        public Export {
            received = List.copyOf(received);
            issued = List.copyOf(issued);
            audit = List.copyOf(audit);
        }
    }

    public CompletableFuture<Export> export(final UUID uuid) {
        final SbPunishments p = SakuraTables.PUNISHMENTS;
        final SbAuditLog log = SakuraTables.AUDIT_LOG;
        return this.storage.read(dsl -> new Export(
                this.mapper.map(dsl, PunishmentMapper.select(dsl)
                        .where(naming(uuid))
                        .orderBy(p.ID.asc()).fetch()),
                this.mapper.map(dsl, PunishmentMapper.select(dsl)
                        .where(p.OPERATOR_KIND.eq(OperatorKind.PLAYER))
                        .and(p.OPERATOR_UUID.eq(uuid))
                        .orderBy(p.ID.asc()).fetch()),
                dsl.selectFrom(log)
                        .where(log.ACTOR_UUID.eq(uuid).or(log.SUBJECT_TYPE.eq(SubjectType.PLAYER)
                                .and(log.SUBJECT_ID.eq(uuid.toString()))))
                        .orderBy(log.ID.asc())
                        .fetch(AuditRepository::entry)));
    }

    public CompletableFuture<SakuraPlayerData.Erase> erase(final UUID uuid, final Actor actor,
            final Instant at) {
        return this.storage.writeRetryingConflicts(configuration -> {
            final DSLContext dsl = configuration.dsl();
            final SbPunishments p = SakuraTables.PUNISHMENTS;
            final SbVictims victims = SakuraTables.VICTIMS;
            final SbPlayerAddresses known = SakuraTables.PLAYER_ADDRESSES;

            final Set<InetAddress> addresses = new LinkedHashSet<>(dsl.select(known.ADDRESS)
                    .from(known).where(known.UUID.eq(uuid)).fetch(known.ADDRESS));
            dsl.select(SakuraTables.PLAYERS.LAST_ADDRESS).from(SakuraTables.PLAYERS)
                    .where(SakuraTables.PLAYERS.UUID.eq(uuid))
                    .and(SakuraTables.PLAYERS.LAST_ADDRESS.isNotNull())
                    .fetch(SakuraTables.PLAYERS.LAST_ADDRESS)
                    .forEach(addresses::add);
            final List<SbVictimsRecord> composites = dsl.selectFrom(victims)
                    .where(victims.UUID.eq(uuid)).and(victims.KIND.eq(VictimKind.COMPOSITE))
                    .and(victims.ADDRESS.isNotNull())
                    .fetch();
            composites.forEach(row -> addresses.add(InetAddresses.fromBytes(row.address())));

            // Victims keep their hash (and so their punishments) but lose the address: the
            // account's composites, and address victims nobody else is known on.
            long cleared = dsl.update(victims).setNull(victims.ADDRESS)
                    .where(victims.UUID.eq(uuid)).and(victims.KIND.eq(VictimKind.COMPOSITE))
                    .and(victims.ADDRESS.isNotNull())
                    .execute();
            if (!addresses.isEmpty()) {
                final SbPlayerAddresses others = SakuraTables.PLAYER_ADDRESSES.as("sb_others");
                cleared += dsl.update(victims).setNull(victims.ADDRESS)
                        .where(victims.KIND.eq(VictimKind.ADDRESS))
                        .and(victims.ADDRESS.isNotNull())
                        .and(victims.ADDRESS_HASH.in(addresses.stream()
                                .map(AddressHashes::of).toList()))
                        .andNotExists(DSL.selectOne().from(others)
                                .where(others.ADDRESS_HASH.eq(victims.ADDRESS_HASH))
                                .and(others.UUID.ne(uuid)))
                        .execute();
            }

            final List<Long> naming = dsl.select(p.ID).from(p)
                    .where(naming(uuid)).fetch(p.ID);
            final long evidence = dsl.deleteFrom(SakuraTables.PUNISHMENT_EVIDENCE)
                    .where(SakuraTables.PUNISHMENT_EVIDENCE.PUNISHMENT_ID.in(naming)).execute();
            final var allowed = SakuraTables.ALLOWED_ADDRESSES;
            final long allowedAddresses = addresses.isEmpty() ? 0 : dsl.deleteFrom(allowed)
                    .where(allowed.ADDRESS.in(addresses)).execute();

            final SbAuditLog log = SakuraTables.AUDIT_LOG;
            for (final InetAddress address : addresses) {
                final String text = address.getHostAddress();
                final String like = "%" + text + "%";
                dsl.update(log)
                        .set(log.OLD_VALUE, DSL.replace(log.OLD_VALUE, text, ERASED))
                        .set(log.NEW_VALUE, DSL.replace(log.NEW_VALUE, text, ERASED))
                        .where(log.OLD_VALUE.like(like).or(log.NEW_VALUE.like(like)))
                        .execute();
            }
            final long names = dsl.deleteFrom(SakuraTables.PLAYER_NAMES)
                    .where(SakuraTables.PLAYER_NAMES.UUID.eq(uuid)).execute();
            final long addressRows = dsl.deleteFrom(known).where(known.UUID.eq(uuid)).execute();
            dsl.deleteFrom(SakuraTables.PLAYERS).where(SakuraTables.PLAYERS.UUID.eq(uuid))
                    .execute();

            final long redacted = redactNames(dsl, uuid);
            final SakuraPlayerData.Erase erased = new SakuraPlayerData.Erase(uuid, names,
                    addressRows, allowedAddresses, cleared, evidence, naming.size(), redacted);
            AuditRepository.append(dsl, at, actor, AuditAction.ERASE, SubjectType.PLAYER,
                    uuid.toString(), null, erased.summary());
            redact(dsl, log, log.ACTOR_KIND, log.ACTOR_UUID, log.ACTOR_NAME, uuid);
            return erased;
        });
    }

    private static long redactNames(final DSLContext dsl, final UUID uuid) {
        final SbPunishments p = SakuraTables.PUNISHMENTS;
        final var evidence = SakuraTables.PUNISHMENT_EVIDENCE;
        final var players = SakuraTables.ALLOWED_PLAYERS;
        final var addresses = SakuraTables.ALLOWED_ADDRESSES;
        final var lockdowns = SakuraTables.LOCKDOWNS;
        final SbAuditLog log = SakuraTables.AUDIT_LOG;
        return redact(dsl, p, p.OPERATOR_KIND, p.OPERATOR_UUID, p.OPERATOR_NAME, uuid)
                + redact(dsl, p, p.REVOKER_KIND, p.REVOKER_UUID, p.REVOKER_NAME, uuid)
                + redact(dsl, p, p.VOIDER_KIND, p.VOIDER_UUID, p.VOIDER_NAME, uuid)
                + redact(dsl, evidence, evidence.ADDED_BY_KIND, evidence.ADDED_BY_UUID,
                        evidence.ADDED_BY_NAME, uuid)
                + redact(dsl, players, players.ADDED_BY_KIND, players.ADDED_BY_UUID,
                        players.ADDED_BY_NAME, uuid)
                + redact(dsl, addresses, addresses.ADDED_BY_KIND, addresses.ADDED_BY_UUID,
                        addresses.ADDED_BY_NAME, uuid)
                + redact(dsl, lockdowns, lockdowns.STARTED_BY_KIND, lockdowns.STARTED_BY_UUID,
                        lockdowns.STARTED_BY_NAME, uuid)
                + redact(dsl, log, log.ACTOR_KIND, log.ACTOR_UUID, log.ACTOR_NAME, uuid);
    }

    private static long redact(final DSLContext dsl, final Table<?> table,
            final Field<OperatorKind> kind, final Field<UUID> uuid, final Field<String> name,
            final UUID account) {
        return dsl.update(table)
                .set(name, ERASED)
                .where(kind.eq(OperatorKind.PLAYER))
                .and(uuid.eq(account))
                .and(name.ne(ERASED))
                .execute();
    }

    private static Condition naming(final UUID uuid) {
        final SbPunishments p = SakuraTables.PUNISHMENTS;
        return p.VICTIM_ID.in(DSL.select(SakuraTables.VICTIMS.ID).from(SakuraTables.VICTIMS)
                .where(SakuraTables.VICTIMS.UUID.eq(uuid))
                .and(SakuraTables.VICTIMS.KIND.in(VictimKind.PLAYER, VictimKind.COMPOSITE)));
    }

}
