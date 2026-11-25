package net.serlith.sakurabans.core.storage.repository;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.serlith.sakurabans.api.appeal.Appeal;
import net.serlith.sakurabans.api.audit.AuditAction;
import net.serlith.sakurabans.api.audit.AuditEntry;
import net.serlith.sakurabans.api.audit.SubjectType;
import net.serlith.sakurabans.api.model.Actor;
import net.serlith.sakurabans.api.model.EndKind;
import net.serlith.sakurabans.api.model.PlayerVictim;
import net.serlith.sakurabans.api.model.Punishment;
import net.serlith.sakurabans.core.storage.StorageService;
import net.serlith.sakurabans.core.storage.dbo.OperatorKind;
import net.serlith.sakurabans.core.storage.dbo.SakuraTables;
import net.serlith.sakurabans.core.storage.dbo.VictimKind;
import net.serlith.sakurabans.core.storage.dbo.records.SbActivePunishmentsRecord;
import net.serlith.sakurabans.core.storage.dbo.tables.SbActivePunishments;
import net.serlith.sakurabans.core.storage.dbo.tables.SbAuditLog;
import net.serlith.sakurabans.core.storage.dbo.tables.SbPunishments;
import net.serlith.sakurabans.core.storage.dbo.tables.SbVictims;
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
            List<Appeal> appeals, List<AuditEntry> audit) {

        public Export {
            received = List.copyOf(received);
            issued = List.copyOf(issued);
            appeals = List.copyOf(appeals);
            audit = List.copyOf(audit);
        }
    }

    public record Erased(long names, long addresses, long expunged, long anonymised,
            long issued, long other) {
    }

    public CompletableFuture<Export> export(final UUID uuid) {
        final SbPunishments p = SakuraTables.PUNISHMENTS;
        final SbAuditLog log = SakuraTables.AUDIT_LOG;
        return this.storage.read(dsl -> new Export(
                this.mapper.map(dsl, PunishmentMapper.select(dsl)
                        .where(StatsRepository.naming(uuid))
                        .orderBy(p.ID.asc()).fetch()),
                this.mapper.map(dsl, PunishmentMapper.select(dsl)
                        .where(p.OPERATOR_KIND.eq(OperatorKind.PLAYER))
                        .and(p.OPERATOR_UUID.eq(uuid))
                        .orderBy(p.ID.asc()).fetch()),
                dsl.selectFrom(SakuraTables.APPEALS)
                        .where(SakuraTables.APPEALS.APPELLANT.eq(uuid))
                        .orderBy(SakuraTables.APPEALS.ID.asc())
                        .fetch(AppealRepository::toAppeal),
                dsl.selectFrom(log)
                        .where(log.ACTOR_UUID.eq(uuid).or(log.SUBJECT_TYPE.eq(SubjectType.PLAYER)
                                .and(log.SUBJECT_ID.eq(uuid.toString()))))
                        .orderBy(log.ID.asc())
                        .fetch(AuditRepository::entry)));
    }

    public CompletableFuture<Erased> erase(final UUID uuid, final Actor actor,
            final Instant at) {
        return this.storage.write(configuration -> {
            final DSLContext dsl = configuration.dsl();
            final SbPunishments p = SakuraTables.PUNISHMENTS;
            final SbVictims victims = SakuraTables.VICTIMS;
            final SbActivePunishments active = SakuraTables.ACTIVE_PUNISHMENTS;
            final Set<String> secrets = new LinkedHashSet<>();
            secrets.add(uuid.toString());
            dsl.select(SakuraTables.PLAYER_ADDRESSES.ADDRESS).from(SakuraTables.PLAYER_ADDRESSES)
                    .where(SakuraTables.PLAYER_ADDRESSES.UUID.eq(uuid))
                    .fetch(SakuraTables.PLAYER_ADDRESSES.ADDRESS)
                    .forEach(address -> secrets.add(address.getHostAddress()));
            final List<Long> composites = dsl.select(victims.ID).from(victims)
                    .where(victims.UUID.eq(uuid)).and(victims.KIND.eq(VictimKind.COMPOSITE))
                    .fetch(victims.ID);
            dsl.select(victims.ADDRESS).from(victims).where(victims.ID.in(composites))
                    .fetch(victims.ADDRESS).forEach(address -> secrets.add(text(address)));

            // Ended punishments naming the account go, like expunges.
            final List<Long> ended = dsl.select(p.ID).from(p)
                    .where(StatsRepository.naming(uuid))
                    .and(p.ID.notIn(DSL.select(active.PUNISHMENT_ID).from(active)))
                    .fetch(p.ID);
            deletePunishments(dsl, ended);

            // Active ones stay, without addresses or evidence.
            final List<Long> stillActive = dsl.select(p.ID).from(p)
                    .where(StatsRepository.naming(uuid)).fetch(p.ID);
            dsl.deleteFrom(SakuraTables.PUNISHMENT_EVIDENCE)
                    .where(SakuraTables.PUNISHMENT_EVIDENCE.PUNISHMENT_ID.in(stillActive))
                    .execute();
            if (!composites.isEmpty()) {
                final long player = VictimRepository.resolve(dsl, PlayerVictim.of(uuid));
                for (final SbActivePunishmentsRecord row : dsl.selectFrom(active)
                        .where(active.VICTIM_ID.in(composites)).fetch()) {
                    final boolean taken = !row.type().stacks() && dsl.fetchExists(active,
                            active.TYPE.eq(row.type()).and(active.VICTIM_ID.eq(player))
                                    .and(active.SCOPE_ID.eq(row.scopeId()))
                                    .and(active.EXCLUSIVITY_SLOT.eq(0L)));
                    if (taken) {
                        PunishmentRepository.endInTransaction(dsl, row.punishmentId(),
                                EndKind.SUPERSEDED, at, null, null);
                    } else {
                        dsl.update(active).set(active.VICTIM_ID, player)
                                .where(active.PUNISHMENT_ID.eq(row.punishmentId())).execute();
                    }
                }
                dsl.update(p).set(p.VICTIM_ID, player).where(p.VICTIM_ID.in(composites))
                        .execute();
                dsl.deleteFrom(victims).where(victims.ID.in(composites)).execute();
            }

            // What it issued keeps its record, without the account.
            final long issued = anonymise(dsl, p, p.OPERATOR_KIND, p.OPERATOR_UUID,
                    p.OPERATOR_NAME, uuid);
            anonymise(dsl, p, p.REVOKER_KIND, p.REVOKER_UUID, p.REVOKER_NAME, uuid);
            anonymise(dsl, p, p.VOIDER_KIND, p.VOIDER_UUID, p.VOIDER_NAME, uuid);
            final var evidence = SakuraTables.PUNISHMENT_EVIDENCE;
            anonymise(dsl, evidence, evidence.ADDED_BY_KIND, evidence.ADDED_BY_UUID,
                    evidence.ADDED_BY_NAME, uuid);
            final var lockdowns = SakuraTables.LOCKDOWNS;
            anonymise(dsl, lockdowns, lockdowns.STARTED_BY_KIND, lockdowns.STARTED_BY_UUID,
                    lockdowns.STARTED_BY_NAME, uuid);
            final var waves = SakuraTables.BAN_WAVES;
            anonymise(dsl, waves, waves.CREATED_BY_KIND, waves.CREATED_BY_UUID,
                    waves.CREATED_BY_NAME, uuid);
            final var entries = SakuraTables.BAN_WAVE_ENTRIES;
            anonymise(dsl, entries, entries.ADDED_BY_KIND, entries.ADDED_BY_UUID,
                    entries.ADDED_BY_NAME, uuid);
            final var appeals = SakuraTables.APPEALS;
            dsl.update(appeals).set(appeals.DECIDED_BY_KIND, OperatorKind.CUSTOM)
                    .setNull(appeals.DECIDED_BY_UUID).set(appeals.DECIDED_BY_NAME, ERASED)
                    .where(appeals.DECIDED_BY_UUID.eq(uuid)).execute();

            // Everything else about it.
            long other = dsl.deleteFrom(appeals).where(appeals.APPELLANT.eq(uuid)).execute();
            other += dsl.deleteFrom(SakuraTables.ALLOWED_PLAYERS)
                    .where(SakuraTables.ALLOWED_PLAYERS.UUID.eq(uuid)).execute();
            final SbAuditLog log = SakuraTables.AUDIT_LOG;
            other += dsl.deleteFrom(log).where(log.SUBJECT_TYPE.eq(SubjectType.PLAYER))
                    .and(log.SUBJECT_ID.eq(uuid.toString())).execute();
            anonymise(dsl, log, log.ACTOR_KIND, log.ACTOR_UUID, log.ACTOR_NAME, uuid);
            for (final String secret : secrets) {
                final String like = "%" + secret + "%";
                dsl.update(log)
                        .set(log.OLD_VALUE, DSL.replace(log.OLD_VALUE, secret, ERASED))
                        .set(log.NEW_VALUE, DSL.replace(log.NEW_VALUE, secret, ERASED))
                        .where(log.OLD_VALUE.like(like).or(log.NEW_VALUE.like(like)))
                        .execute();
            }
            final long names = dsl.deleteFrom(SakuraTables.PLAYER_NAMES)
                    .where(SakuraTables.PLAYER_NAMES.UUID.eq(uuid)).execute();
            final long addresses = dsl.deleteFrom(SakuraTables.PLAYER_ADDRESSES)
                    .where(SakuraTables.PLAYER_ADDRESSES.UUID.eq(uuid)).execute();
            dsl.deleteFrom(SakuraTables.PLAYERS).where(SakuraTables.PLAYERS.UUID.eq(uuid))
                    .execute();
            final Erased erased = new Erased(names, addresses, ended.size(), stillActive.size(),
                    issued, other);
            AuditRepository.append(dsl, at, actor, AuditAction.ERASE, SubjectType.PLAYER,
                    uuid.toString(), null, erased.toString());
            return erased;
        });
    }

    private static void deletePunishments(final DSLContext dsl, final List<Long> ids) {
        if (ids.isEmpty()) {
            return;
        }
        dsl.deleteFrom(SakuraTables.PUNISHMENT_EVIDENCE)
                .where(SakuraTables.PUNISHMENT_EVIDENCE.PUNISHMENT_ID.in(ids)).execute();
        dsl.deleteFrom(SakuraTables.APPEALS)
                .where(SakuraTables.APPEALS.PUNISHMENT_ID.in(ids)).execute();
        dsl.update(SakuraTables.BAN_WAVE_ENTRIES)
                .setNull(SakuraTables.BAN_WAVE_ENTRIES.PUNISHMENT_ID)
                .where(SakuraTables.BAN_WAVE_ENTRIES.PUNISHMENT_ID.in(ids)).execute();
        dsl.deleteFrom(SakuraTables.ACTIVE_PUNISHMENTS)
                .where(SakuraTables.ACTIVE_PUNISHMENTS.PUNISHMENT_ID.in(ids)).execute();
        dsl.deleteFrom(SakuraTables.PUNISHMENTS).where(SakuraTables.PUNISHMENTS.ID.in(ids))
                .execute();
    }

    private static long anonymise(final DSLContext dsl, final Table<?> table,
            final Field<OperatorKind> kind, final Field<UUID> uuid, final Field<String> name,
            final UUID account) {
        final Condition mine = kind.eq(OperatorKind.PLAYER).and(uuid.eq(account));
        return dsl.update(table)
                .set(kind, OperatorKind.CUSTOM)
                .setNull(uuid)
                .set(name, ERASED)
                .where(mine)
                .execute();
    }

    private static String text(final byte[] address) {
        try {
            return InetAddress.getByAddress(address).getHostAddress();
        } catch (final UnknownHostException e) {
            throw new IllegalStateException(e);
        }
    }
}
