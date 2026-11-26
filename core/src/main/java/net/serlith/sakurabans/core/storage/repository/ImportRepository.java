package net.serlith.sakurabans.core.storage.repository;

import com.google.common.base.Preconditions;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;
import net.serlith.sakurabans.api.model.Actor;
import net.serlith.sakurabans.api.model.EndKind;
import net.serlith.sakurabans.api.model.Operator;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.core.model.AuditAction;
import net.serlith.sakurabans.core.model.ImportedPlayer;
import net.serlith.sakurabans.core.model.SubjectType;
import net.serlith.sakurabans.core.storage.StorageService;
import net.serlith.sakurabans.core.storage.dbo.OperatorKind;
import net.serlith.sakurabans.core.storage.dbo.SakuraTables;
import net.serlith.sakurabans.core.storage.dbo.SbTable;
import net.serlith.sakurabans.core.storage.dbo.records.SbActivePunishmentsRecord;
import net.serlith.sakurabans.core.storage.dbo.records.SbPlayerAddressesRecord;
import net.serlith.sakurabans.core.storage.dbo.records.SbPlayerNamesRecord;
import net.serlith.sakurabans.core.storage.dbo.records.SbPlayersRecord;
import net.serlith.sakurabans.core.storage.dbo.tables.SbActivePunishments;
import net.serlith.sakurabans.core.storage.dbo.tables.SbPunishments;
import net.serlith.sakurabans.core.util.AddressHashes;
import org.jooq.Cursor;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.InsertValuesStepN;
import org.jooq.Record;
import org.jooq.ResultQuery;
import org.jooq.SQLDialect;
import org.jooq.Table;
import org.jooq.exception.DataAccessException;
import org.jooq.impl.DSL;
import org.jspecify.annotations.Nullable;

public final class ImportRepository {

    static final String SEED_SOURCE = "config";
    static final String SCHEMA_VERSION_KEY = "schema_version";
    static final String DISPLAY_ID_SECRET_KEY = "display_id_secret";
    private static final int MAX_BINDS = 30_000;

    private final StorageService storage;

    public ImportRepository(final StorageService storage) {
        this.storage = storage;
    }

    public record BatchOutcome(int imported, int skipped, int superseded, int players,
            List<ImportedPlayer> unknownPlayers) {

        public BatchOutcome {
            unknownPlayers = List.copyOf(unknownPlayers);
        }
    }

    public CompletableFuture<Set<String>> existing(final Collection<String> externalIds) {
        if (externalIds.isEmpty()) {
            return CompletableFuture.completedFuture(Set.of());
        }
        final List<String> ids = List.copyOf(externalIds);
        return this.storage.read(dsl -> Set.copyOf(existing(dsl, ids)));
    }

    private static Set<String> existing(final DSLContext dsl, final Collection<String> ids) {
        final SbPunishments p = SakuraTables.PUNISHMENTS;
        return new HashSet<>(dsl.select(p.EXTERNAL_ID).from(p).where(p.EXTERNAL_ID.in(ids))
                .fetch(p.EXTERNAL_ID));
    }

    public CompletableFuture<BatchOutcome> write(final List<ImportedPlayer> players,
            final List<ImportRow> rows, final Instant now) {
        final List<ImportedPlayer> playerCopy = List.copyOf(players);
        final List<ImportRow> rowCopy = List.copyOf(rows);
        return this.storage.writeRetryingConflicts(configuration -> {
            final DSLContext dsl = configuration.dsl();
            int merged = 0;
            final List<ImportedPlayer> unknown = new ArrayList<>();
            for (final ImportedPlayer player : playerCopy) {
                if (mergePlayer(dsl, player)) {
                    merged++;
                } else {
                    unknown.add(player);
                }
            }
            final Set<String> taken = existing(dsl, rowCopy.stream()
                    .map(row -> row.punishment().externalId())
                    .filter(id -> id != null)
                    .toList());
            int imported = 0;
            int skipped = 0;
            int superseded = 0;
            for (final ImportRow row : rowCopy) {
                final String externalId = row.punishment().externalId();
                if (externalId != null && !taken.add(externalId)) {
                    skipped++;
                    continue;
                }
                if (insert(dsl, row, now)) {
                    superseded++;
                }
                imported++;
            }
            return new BatchOutcome(imported, skipped, superseded, merged, unknown);
        });
    }

    private static boolean insert(final DSLContext dsl, final ImportRow row, final Instant now) {
        final NewPunishment punishment = row.punishment();
        final PunishmentType type = punishment.type();
        final long victimId = VictimRepository.resolve(dsl, punishment.victim());
        final int scopeId = ScopeRepository.resolve(dsl, punishment.scope());
        EndKind endKind = row.endKind();
        Instant endedAt = row.endedAt();
        boolean active = endKind == null && !type.instant();
        final Instant expiry = punishment.expiresAt();
        if (active && expiry != null && !expiry.isAfter(now)) {
            endKind = EndKind.EXPIRED;
            endedAt = expiry;
            active = false;
        }
        boolean superseded = false;
        if (active && !type.stacks()) {
            final SbActivePunishments slots = SakuraTables.ACTIVE_PUNISHMENTS;
            final SbActivePunishmentsRecord occupant = dsl.selectFrom(slots)
                    .where(slots.TYPE.eq(type))
                    .and(slots.VICTIM_ID.eq(victimId))
                    .and(slots.SCOPE_ID.eq(scopeId))
                    .forUpdate()
                    .fetchOne();
            if (occupant != null) {
                final Instant occupantExpiry = occupant.expiresAt();
                final Instant occupantCreated = dsl.select(SakuraTables.PUNISHMENTS.CREATED_AT)
                        .from(SakuraTables.PUNISHMENTS)
                        .where(SakuraTables.PUNISHMENTS.ID.eq(occupant.punishmentId()))
                        .fetchSingle(SakuraTables.PUNISHMENTS.CREATED_AT);
                if (occupantExpiry != null && !occupantExpiry.isAfter(now)) {
                    PunishmentRepository.endInTransaction(dsl, occupant.punishmentId(),
                            EndKind.EXPIRED, occupantExpiry, null, null);
                } else if (occupantCreated.isAfter(punishment.createdAt())) {
                    // The one already there is newer: this one was replaced by it.
                    endKind = EndKind.SUPERSEDED;
                    endedAt = occupantCreated;
                    active = false;
                    superseded = true;
                } else {
                    PunishmentRepository.endInTransaction(dsl, occupant.punishmentId(),
                            EndKind.SUPERSEDED, punishment.createdAt(), null, null);
                    superseded = true;
                }
            }
        }
        final boolean ended = endKind != null;
        final long id = insertRow(dsl, row, victimId, scopeId, endKind, endedAt,
                ended ? row.endedBy() : null, ended ? row.endReason() : null);
        if (active) {
            final SbActivePunishments slots = SakuraTables.ACTIVE_PUNISHMENTS;
            dsl.insertInto(slots)
                    .set(slots.PUNISHMENT_ID, id)
                    .set(slots.TYPE, type)
                    .set(slots.VICTIM_ID, victimId)
                    .set(slots.SCOPE_ID, scopeId)
                    .set(slots.EXPIRES_AT, expiry)
                    .set(slots.EXCLUSIVITY_SLOT, type.stacks() ? id : 0L)
                    .execute();
        }
        return superseded;
    }

    private static long insertRow(final DSLContext dsl, final ImportRow row, final long victimId,
            final int scopeId, final @Nullable EndKind endKind, final @Nullable Instant endedAt,
            final @Nullable Operator endedBy,
            final @Nullable String endReason) {
        final SbPunishments p = SakuraTables.PUNISHMENTS;
        final NewPunishment punishment = row.punishment();
        final StoredOperator operator = StoredOperator.of(punishment.actor().operator());
        final StoredOperator revoker = endedBy == null ? null : StoredOperator.of(endedBy);
        final String source = punishment.actor().source();
        final boolean voided = endKind == EndKind.VOIDED;
        final StoredOperator voider = !voided ? null
                : revoker != null ? revoker
                : new StoredOperator(OperatorKind.CONSOLE, null, StoredOperator.CONSOLE_NAME);
        return dsl.insertInto(p)
                .set(p.TYPE, punishment.type())
                .set(p.SHADOW, punishment.shadow())
                .set(p.VICTIM_ID, victimId)
                .set(p.SCOPE_ID, scopeId)
                .set(p.REASON, punishment.reason())
                .set(p.OPERATOR_KIND, operator.kind())
                .set(p.OPERATOR_UUID, operator.uuid())
                .set(p.OPERATOR_NAME, operator.name())
                .set(p.SOURCE, source)
                .set(p.CREATED_AT, punishment.createdAt())
                .set(p.EXPIRES_AT, punishment.expiresAt())
                .set(p.ORIGIN_SERVER, punishment.originServer())
                .set(p.VISIBILITY, punishment.visibility())
                .set(p.TEMPLATE, punishment.template())
                .set(p.TEMPLATE_STEP, punishment.templateStep() == null ? null
                        : punishment.templateStep().shortValue())
                .set(p.ENDED_AT, endedAt)
                .set(p.END_KIND, endKind)
                .set(p.REVOKER_KIND, revoker == null ? null : revoker.kind())
                .set(p.REVOKER_UUID, revoker == null ? null : revoker.uuid())
                .set(p.REVOKER_NAME, revoker == null ? null : revoker.name())
                .set(p.REVOKER_SOURCE, revoker == null ? null : source)
                .set(p.REVOKE_REASON, endReason)
                .set(p.VOIDED_AT, voided ? endedAt : null)
                .set(p.VOIDER_KIND, voider == null ? null : voider.kind())
                .set(p.VOIDER_UUID, voider == null ? null : voider.uuid())
                .set(p.VOIDER_NAME, voider == null ? null : voider.name())
                .set(p.VOIDER_SOURCE, voider == null ? null : source)
                .set(p.VOID_REASON, voided ? endReason : null)
                .set(p.EXTERNAL_ID, punishment.externalId())
                // Old warnings must not greet their players as new ones.
                .set(p.ACKNOWLEDGED, true)
                .returningResult(p.ID)
                .fetchSingle()
                .value1();
    }

    static boolean mergePlayer(final DSLContext dsl, final ImportedPlayer player) {
        final var players = SakuraTables.PLAYERS;
        final SbPlayersRecord existing = dsl.selectFrom(players)
                .where(players.UUID.eq(player.uuid())).forUpdate().fetchOne();
        final String name = player.name();
        if (existing == null) {
            if (name == null) {
                return false;
            }
            dsl.insertInto(players)
                    .set(players.UUID, player.uuid())
                    .set(players.LAST_NAME, name)
                    .set(players.LAST_NAME_LOWER, name.toLowerCase(Locale.ROOT))
                    .set(players.FIRST_SEEN, player.firstSeen())
                    .set(players.LAST_SEEN, player.lastSeen())
                    .set(players.LAST_ADDRESS, player.address())
                    .execute();
        } else {
            final boolean newer = !player.lastSeen().isBefore(existing.lastSeen());
            dsl.update(players)
                    .set(players.FIRST_SEEN, earliest(existing.firstSeen(), player.firstSeen()))
                    .set(players.LAST_SEEN, latest(existing.lastSeen(), player.lastSeen()))
                    .set(players.LAST_NAME, newer && name != null ? name : existing.lastName())
                    .set(players.LAST_NAME_LOWER, newer && name != null
                            ? name.toLowerCase(Locale.ROOT) : existing.lastNameLower())
                    .set(players.LAST_ADDRESS, newer && player.address() != null
                            ? player.address() : existing.lastAddress())
                    .where(players.UUID.eq(player.uuid()))
                    .execute();
        }
        if (name != null) {
            final var names = SakuraTables.PLAYER_NAMES;
            final String lower = name.toLowerCase(Locale.ROOT);
            final SbPlayerNamesRecord known = dsl.selectFrom(names)
                    .where(names.UUID.eq(player.uuid())).and(names.NAME_LOWER.eq(lower))
                    .forUpdate().fetchOne();
            if (known == null) {
                dsl.insertInto(names)
                        .set(names.UUID, player.uuid())
                        .set(names.NAME, name)
                        .set(names.NAME_LOWER, lower)
                        .set(names.FIRST_SEEN, player.firstSeen())
                        .set(names.LAST_SEEN, player.lastSeen())
                        .execute();
            } else {
                dsl.update(names)
                        .set(names.NAME, player.lastSeen().isBefore(known.lastSeen())
                                ? known.name() : name)
                        .set(names.FIRST_SEEN, earliest(known.firstSeen(), player.firstSeen()))
                        .set(names.LAST_SEEN, latest(known.lastSeen(), player.lastSeen()))
                        .where(names.UUID.eq(player.uuid())).and(names.NAME_LOWER.eq(lower))
                        .execute();
            }
        }
        if (player.address() != null) {
            final var addresses = SakuraTables.PLAYER_ADDRESSES;
            final SbPlayerAddressesRecord known = dsl.selectFrom(addresses)
                    .where(addresses.UUID.eq(player.uuid()))
                    .and(addresses.ADDRESS.eq(player.address()))
                    .forUpdate().fetchOne();
            if (known == null) {
                dsl.insertInto(addresses)
                        .set(addresses.UUID, player.uuid())
                        .set(addresses.ADDRESS, player.address())
                        .set(addresses.ADDRESS_HASH, AddressHashes.of(player.address()))
                        .set(addresses.FIRST_SEEN, player.firstSeen())
                        .set(addresses.LAST_SEEN, player.lastSeen())
                        .execute();
            } else {
                dsl.update(addresses)
                        .set(addresses.FIRST_SEEN,
                                earliest(known.firstSeen(), player.firstSeen()))
                        .set(addresses.LAST_SEEN, latest(known.lastSeen(), player.lastSeen()))
                        .where(addresses.UUID.eq(player.uuid()))
                        .and(addresses.ADDRESS.eq(player.address()))
                        .execute();
            }
        }
        return true;
    }

    private static Instant earliest(final Instant a, final Instant b) {
        return a.isBefore(b) ? a : b;
    }

    private static Instant latest(final Instant a, final Instant b) {
        return a.isAfter(b) ? a : b;
    }

    public CompletableFuture<Long> audit(final Instant at, final Actor actor,
            final String subject, final String summary) {
        return this.storage.write(configuration -> AuditRepository.append(configuration.dsl(),
                at, actor, AuditAction.IMPORT, SubjectType.IMPORT, subject, null, summary));
    }

    // Self import (copy of another SakuraBans database)

    public static List<SbTable<?>> copiedTables() {
        return SakuraTables.all().stream()
                .filter(table -> table != SakuraTables.META && table != SakuraTables.SYNC_MESSAGES
                        && table != SakuraTables.EDITOR_SESSIONS
                        && table != SakuraTables.INSTANCES)
                .toList();
    }

    public static String baseName(final SbTable<?> table) {
        return table.getName().substring(SakuraTables.prefix().length());
    }

    public CompletableFuture<List<String>> occupied() {
        return this.storage.read(dsl -> {
            final List<String> occupied = new ArrayList<>();
            for (final SbTable<?> table : copiedTables()) {
                final int rows;
                if (table == SakuraTables.AUDIT_LOG) {
                    rows = dsl.fetchCount(table, SakuraTables.AUDIT_LOG.SOURCE.ne(SEED_SOURCE));
                } else if (table == SakuraTables.SCOPES
                        // Only names; what uses them is checked by its own table.
                        || table == SakuraTables.TEMPLATES
                        || table == SakuraTables.TEMPLATE_STEPS
                        || table == SakuraTables.TEMPLATE_GROUPS
                        || table == SakuraTables.TEMPLATE_GROUP_MEMBERS) {
                    // Seeded from templates.yml; changes by staff show in the audit log.
                    rows = 0;
                } else {
                    rows = dsl.fetchCount(table);
                }
                if (rows > 0) {
                    occupied.add(baseName(table));
                }
            }
            return List.copyOf(occupied);
        });
    }

    public static Optional<String> sourceVersion(final DSLContext source,
            final String prefix) {
        final Table<Record> meta = DSL.table(DSL.quotedName(prefix + "meta"));
        final Field<String> key = DSL.field(DSL.quotedName("k"), String.class);
        final Field<String> value = DSL.field(DSL.quotedName("v"), String.class);
        try {
            return source.select(value).from(meta).where(key.eq(SCHEMA_VERSION_KEY))
                    .fetchOptional(value);
        } catch (final DataAccessException e) {
            return Optional.empty();
        }
    }

    public static Map<String, Long> sourceCounts(final DSLContext source, final String prefix) {
        final Map<String, Long> counts = new LinkedHashMap<>();
        for (final SbTable<?> table : copiedTables()) {
            counts.put(baseName(table), (long) source.fetchCount(
                    DSL.table(DSL.quotedName(prefix + baseName(table)))));
        }
        return counts;
    }

    public CompletableFuture<Map<String, Long>> copy(final DSLContext source,
            final String prefix, final int batchSize,
            final Function<ResultQuery<Record>, Cursor<Record>> read,
            final Consumer<String> progress) {
        return this.storage.write(configuration -> {
            final DSLContext dsl = configuration.dsl();
            final List<SbTable<?>> tables = copiedTables();
            for (final SbTable<?> table : tables.reversed()) {
                dsl.deleteFrom(table).execute();
            }
            final Map<String, Long> copied = new LinkedHashMap<>();
            for (final SbTable<?> table : tables) {
                final long rows = copyTable(source, prefix, dsl, table, batchSize, read);
                copied.put(baseName(table), rows);
                progress.accept(baseName(table) + ": " + rows);
            }
            final Field<String> key = DSL.field(DSL.quotedName("k"), String.class);
            final Field<String> value = DSL.field(DSL.quotedName("v"), String.class);
            final String secret = source.select(value)
                    .from(DSL.table(DSL.quotedName(prefix + "meta")))
                    .where(key.eq(DISPLAY_ID_SECRET_KEY))
                    .fetchOptional(value).orElse(null);
            if (secret != null) {
                // Display ids stay the same codes as in the source.
                dsl.insertInto(SakuraTables.META)
                        .set(SakuraTables.META.K, DISPLAY_ID_SECRET_KEY)
                        .set(SakuraTables.META.V, secret)
                        .onDuplicateKeyUpdate()
                        .set(SakuraTables.META.V, secret)
                        .execute();
            }
            return copied;
        });
    }

    private static long copyTable(final DSLContext source, final String prefix,
            final DSLContext dsl, final SbTable<?> table, final int batchSize,
            final Function<ResultQuery<Record>, Cursor<Record>> read) {
        final Field<?>[] fields = table.fields();
        final List<Field<?>> columns = new ArrayList<>(fields.length);
        for (final Field<?> field : fields) {
            columns.add(DSL.field(DSL.quotedName(field.getName()), field.getDataType()));
        }
        final int rowsPerInsert = Math.max(1, Math.min(batchSize, MAX_BINDS / fields.length));
        long copied = 0;
        try (Cursor<Record> cursor = read.apply(source.select(columns)
                .from(DSL.table(DSL.quotedName(prefix + baseName(table)))))) {
            final List<Record> batch = new ArrayList<>(rowsPerInsert);
            for (final Record record : cursor) {
                batch.add(record);
                if (batch.size() == rowsPerInsert) {
                    copied += insertAll(dsl, table, fields, batch);
                    batch.clear();
                }
            }
            if (!batch.isEmpty()) {
                copied += insertAll(dsl, table, fields, batch);
            }
        }
        return copied;
    }

    private static int insertAll(final DSLContext dsl, final SbTable<?> table,
            final Field<?>[] fields, final List<Record> rows) {
        InsertValuesStepN<?> insert = dsl.insertInto(table).columns(fields);
        for (final Record row : rows) {
            insert = insert.values(row.intoArray());
        }
        insert.execute();
        return rows.size();
    }

    public CompletableFuture<Void> resetIdentities() {
        return this.storage.read(dsl -> {
            for (final SbTable<?> table : copiedTables()) {
                for (final Field<?> field : table.fields()) {
                    if (field.getDataType().identity()) {
                        resetIdentity(dsl, table, field);
                    }
                }
            }
            return null;
        });
    }

    private static void resetIdentity(final DSLContext dsl, final SbTable<?> table,
            final Field<?> id) {
        final Number found = (Number) dsl.select(DSL.max(id)).from(table).fetchOne(0);
        final long max = found == null ? 0L : found.longValue();
        final SQLDialect dialect = dsl.dialect().family();
        switch (dialect) {
            case H2 -> dsl.execute("ALTER TABLE {0} ALTER COLUMN {1} RESTART WITH {2}", table,
                    DSL.name(id.getName()), DSL.inline(max + 1));
            case MARIADB, MYSQL -> dsl.execute("ALTER TABLE {0} AUTO_INCREMENT = {1}", table,
                    DSL.inline(max + 1));
            case POSTGRES -> dsl.fetch("SELECT setval(pg_get_serial_sequence({0}, {1}), {2},"
                            + " {3})", DSL.inline(dsl.render(table)), DSL.inline(id.getName()),
                    DSL.inline(Math.max(max, 1L)), DSL.inline(max > 0));
            default -> Preconditions.checkState(false, "no identity reset for %s", dialect);
        }
    }

}
