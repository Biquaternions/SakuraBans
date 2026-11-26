package net.serlith.sakurabans.core.importer;

import java.net.InetAddress;
import java.time.Instant;
import java.time.InstantSource;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.serlith.sakurabans.api.model.AddressVictim;
import net.serlith.sakurabans.api.model.CategoryScope;
import net.serlith.sakurabans.api.model.CompositeVictim;
import net.serlith.sakurabans.api.model.ConsoleOperator;
import net.serlith.sakurabans.api.model.EndKind;
import net.serlith.sakurabans.api.model.GlobalScope;
import net.serlith.sakurabans.api.model.Operator;
import net.serlith.sakurabans.api.model.PlayerVictim;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.model.Scope;
import net.serlith.sakurabans.api.model.ServerScope;
import net.serlith.sakurabans.api.model.Victim;
import net.serlith.sakurabans.api.model.Visibility;
import net.serlith.sakurabans.api.validation.Validator;
import net.serlith.sakurabans.core.configuration.settings.ImportSettings;
import net.serlith.sakurabans.core.model.ImportedPlayer;
import net.serlith.sakurabans.core.util.Uuids;
import org.jooq.Cursor;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.jspecify.annotations.Nullable;

/**
 * LibertyBans 1.1: {@code history} lists every punishment (kicks too), {@code bans},
 * {@code mutes} and {@code warns} the active ones; {@code punishments}, {@code victims} and
 * {@code scopes} hold their data, {@code names} and {@code addresses} the players.
 *
 * <p>The base tables are read, not the {@code simple_*} views: the views were redefined by
 * three migrations (tracks, scopes, the scope length), they inner-join the victim (a row
 * without one would vanish instead of being reported) and an active punishment is just a row
 * of {@code bans}/{@code mutes}/{@code warns}, which a left join shows directly.
 *
 * <p>LibertyBans quotes every name (lower case, with the prefix), stores times in epoch seconds
 * ({@code end} 0 is permanent), UUIDs as {@code UUID} (HSQLDB, PostgreSQL) or
 * {@code BINARY(16)} (MariaDB, MySQL), addresses as 4 or 16 bytes, and the console operator
 * as the nil UUID. A revocation deletes the row from the active table and records nothing
 * else; an expired row is deleted there by a periodic sweep, or not yet.
 */
public final class LibertyBansSource implements ImportSource {

    private static final String PUNISHMENTS = "punishments";

    // LibertyBans' PunishmentType, Victim.VictimType and ScopeType, stored by ordinal.
    private static final PunishmentType[] TYPES = {
        PunishmentType.BAN, PunishmentType.MUTE, PunishmentType.WARN, PunishmentType.KICK,
    };
    private static final int PLAYER_VICTIM = 0;
    private static final int ADDRESS_VICTIM = 1;
    private static final int COMPOSITE_VICTIM = 2;
    private static final int SERVER_SCOPE = 1;
    private static final int CATEGORY_SCOPE = 2;
    private static final int NIL_BYTES = 16;

    private static final Field<Object> UUID_COLUMN = column(null, "uuid", Object.class);
    private static final Field<String> NAME = column(null, "name", String.class);
    private static final Field<Object> ADDRESS = column(null, "address", Object.class);
    private static final Field<Long> UPDATED = column(null, "updated", Long.class);

    private static final Field<Long> ID = column("h", "id", Long.class);
    private static final Field<Long> PUNISHMENT_ID = column("p", "id", Long.class);
    private static final Field<Integer> TYPE = column("p", "type", Integer.class);
    private static final Field<Object> OPERATOR = column("p", "operator", Object.class);
    private static final Field<String> REASON = column("p", "reason", String.class);
    private static final Field<String> LEGACY_SCOPE = column("p", "scope", String.class);
    private static final Field<Long> START = column("p", "start", Long.class);
    private static final Field<Long> END = column("p", "end", Long.class);
    private static final Field<Integer> SCOPE_ID = column("p", "scope_id", Integer.class);
    private static final Field<Integer> SCOPE_TYPE = column("s", "type", Integer.class);
    private static final Field<String> SCOPE_VALUE = column("s", "value", String.class);
    private static final Field<Integer> VICTIM_ID = column("v", "id", Integer.class);
    private static final Field<Integer> VICTIM_TYPE = column("v", "type", Integer.class);
    private static final Field<Object> VICTIM_UUID = column("v", "uuid", Object.class);
    private static final Field<Object> VICTIM_ADDRESS = column("v", "address", Object.class);
    private static final Field<Long> BANNED = column("b", "id", Long.class);
    private static final Field<Long> MUTED = column("m", "id", Long.class);
    private static final Field<Long> WARNED = column("w", "id", Long.class);

    private final InstantSource clock;

    public LibertyBansSource() {
        this(InstantSource.system());
    }

    LibertyBansSource(final InstantSource clock) {
        this.clock = clock;
    }

    private record Named(String name, long updated) {
    }

    @Override
    public String id() {
        return ImportSettings.LIBERTYBANS;
    }

    @Override
    public Optional<String> databaseSection() {
        return Optional.of(ImportSettings.LIBERTYBANS);
    }

    @Override
    public Map<String, Long> survey(final SourceContext context) {
        final SourceDatabase database = context.database();
        final Map<String, Long> rows = new LinkedHashMap<>();
        for (final String table : new String[] {"names", "addresses", "history"}) {
            rows.put(table, (long) database.dsl().fetchCount(table(database, table)));
        }
        return rows;
    }

    @Override
    public void read(final SourceContext context, final SourceReader reader) {
        final SourceDatabase database = context.database();
        this.players(database, reader, "names");
        this.players(database, reader, "addresses");
        final Map<UUID, String> operators = operatorNames(database);
        final Instant now = this.clock.instant();
        try (Cursor<? extends Record> rows = database.stream(database.dsl()
                .select(ID, PUNISHMENT_ID, TYPE, OPERATOR, REASON, LEGACY_SCOPE, START, END,
                        SCOPE_ID, SCOPE_TYPE, SCOPE_VALUE, VICTIM_ID, VICTIM_TYPE, VICTIM_UUID,
                        VICTIM_ADDRESS, BANNED, MUTED, WARNED)
                .from(table(database, "history").as("h"))
                .leftJoin(table(database, PUNISHMENTS).as("p")).on(PUNISHMENT_ID.eq(ID))
                .leftJoin(table(database, "victims").as("v"))
                .on(VICTIM_ID.eq(column("h", "victim", Integer.class)))
                .leftJoin(table(database, "scopes").as("s"))
                .on(column("s", "id", Integer.class).eq(SCOPE_ID))
                .leftJoin(table(database, "bans").as("b")).on(BANNED.eq(ID))
                .leftJoin(table(database, "mutes").as("m")).on(MUTED.eq(ID))
                .leftJoin(table(database, "warns").as("w")).on(WARNED.eq(ID))
                .orderBy(ID))) {
            for (final Record row : rows) {
                final String problem = this.punishment(context, reader, row, operators, now);
                if (problem != null) {
                    reader.failure(PUNISHMENTS, String.valueOf(row.get(ID)), problem);
                }
            }
        }
    }

    private void players(final SourceDatabase database, final SourceReader reader,
            final String table) {
        final boolean names = table.equals("names");
        try (Cursor<? extends Record> rows = database.stream(database.dsl()
                .select(UUID_COLUMN, names ? NAME : ADDRESS, UPDATED)
                .from(table(database, table))
                .orderBy(UPDATED))) {
            for (final Record row : rows) {
                final Object rawUuid = row.get(UUID_COLUMN);
                final UUID uuid = ImportValues.uuid(rawUuid).orElse(null);
                final Instant seen = Instant.ofEpochSecond(Math.max(0L, row.get(UPDATED)));
                if (names) {
                    final String name = row.get(NAME);
                    if (uuid == null) {
                        reader.failure(table, String.valueOf(name), "not a player UUID");
                    } else {
                        reader.player(ImportedPlayer.named(uuid, name, seen));
                    }
                    continue;
                }
                final InetAddress address = ImportValues.address(row.get(ADDRESS)).orElse(null);
                if (uuid == null || address == null) {
                    reader.failure(table, uuid == null ? String.valueOf(rawUuid)
                            : uuid.toString(), "not a player UUID and an address");
                } else {
                    reader.player(ImportedPlayer.at(uuid, address, seen));
                }
            }
        }
    }

    private static Map<UUID, String> operatorNames(final SourceDatabase database) {
        final Map<UUID, Named> latest = new HashMap<>();
        database.dsl()
                .select(UUID_COLUMN, NAME, UPDATED)
                .from(table(database, "names"))
                .where(UUID_COLUMN.in(database.dsl()
                        .select(column(null, "operator", Object.class))
                        .from(table(database, PUNISHMENTS))))
                .fetch()
                .forEach(row -> ImportValues.uuid(row.get(UUID_COLUMN)).ifPresent(uuid ->
                        latest.merge(uuid, new Named(row.get(NAME), row.get(UPDATED)),
                                (a, b) -> b.updated() > a.updated() ? b : a)));
        final Map<UUID, String> names = new HashMap<>();
        latest.forEach((uuid, named) -> names.put(uuid, named.name()));
        return names;
    }

    private @Nullable String punishment(final SourceContext context, final SourceReader reader,
            final Record row, final Map<UUID, String> operators, final Instant now) {
        if (row.get(PUNISHMENT_ID) == null) {
            return "a history row without its punishment";
        }
        final Integer typeCode = row.get(TYPE);
        if (typeCode == null || typeCode < 0 || typeCode >= TYPES.length) {
            return "unknown punishment type " + typeCode;
        }
        final PunishmentType type = TYPES[typeCode];
        final Victim victim = victim(row);
        if (victim == null) {
            return "unknown or incomplete victim " + row.get(VICTIM_ID) + " (type "
                    + row.get(VICTIM_TYPE) + ")";
        }
        final Scope scope = scope(row);
        if (scope == null) {
            return "not a valid scope: " + (row.get(SCOPE_ID) == null
                    ? row.get(LEGACY_SCOPE) : row.get(SCOPE_VALUE));
        }
        final Instant start = ImportValues.seconds(row.get(START)).orElse(null);
        if (start == null) {
            return "no start time";
        }
        final Operator operator = operator(row.get(OPERATOR), operators).orElse(null);
        if (operator == null) {
            return "not an operator UUID: " + row.get(OPERATOR);
        }
        // A kick can't be temporary in LibertyBans; its end is 0.
        final Instant end = type.instant() ? null
                : ImportValues.seconds(row.get(END)).orElse(null);
        final ImportedPunishment.End ended;
        if (end != null && !end.isAfter(now)) {
            ended = new ImportedPunishment.End(EndKind.EXPIRED, end, null, null);
        } else if (!type.instant() && !active(type, row)) {
            // Revoked: LibertyBans keeps neither when nor by whom, so it ends where it began
            // and the runner names the unknown revoker.
            ended = new ImportedPunishment.End(EndKind.REVOKED, start, null, null);
        } else {
            ended = null;
        }
        reader.punishment(new ImportedPunishment(type, victim, operator, start, end, scope,
                reader.reason(row.get(REASON)), Visibility.NORMAL, false, null, ended,
                context.externalId(PUNISHMENTS, row.get(ID))));
        return null;
    }

    private static boolean active(final PunishmentType type, final Record row) {
        return switch (type) {
            case BAN -> row.get(BANNED) != null;
            case MUTE -> row.get(MUTED) != null;
            case WARN -> row.get(WARNED) != null;
            case KICK -> false;
        };
    }

    // A player victim stores 0.0.0.0, an address victim the nil UUID: the type says which
    // column counts.
    private static @Nullable Victim victim(final Record row) {
        final Integer type = row.get(VICTIM_TYPE);
        if (type == null) {
            return null;
        }
        final UUID uuid = ImportValues.uuid(row.get(VICTIM_UUID)).orElse(null);
        final InetAddress address = ImportValues.address(row.get(VICTIM_ADDRESS)).orElse(null);
        return switch (type) {
            case PLAYER_VICTIM -> uuid == null ? null : PlayerVictim.of(uuid);
            case ADDRESS_VICTIM -> address == null ? null : AddressVictim.of(address);
            case COMPOSITE_VICTIM -> uuid == null || address == null ? null
                    : CompositeVictim.of(uuid, address);
            default -> null;
        };
    }

    // scope_id null is global; the textual scope column predates the scopes table (V36) and
    // was a server name, kept for rows a failed V38 left behind.
    private static @Nullable Scope scope(final Record row) {
        if (row.get(SCOPE_ID) == null) {
            final String legacy = row.get(LEGACY_SCOPE);
            return legacy == null || legacy.isEmpty() ? GlobalScope.INSTANCE
                    : Validator.normalizeIdentifier(legacy).map(ServerScope::of).orElse(null);
        }
        final Integer type = row.get(SCOPE_TYPE);
        final String value = row.get(SCOPE_VALUE);
        if (type == null || value == null) {
            return null;
        }
        final Optional<String> name = Validator.normalizeIdentifier(value);
        return switch (type) {
            case SERVER_SCOPE -> name.map(ServerScope::of).orElse(null);
            case CATEGORY_SCOPE -> name.map(CategoryScope::of).orElse(null);
            default -> null;
        };
    }

    // Empty when the column is neither the console (the nil UUID) nor a UUID.
    private static Optional<Operator> operator(final @Nullable Object raw,
            final Map<UUID, String> names) {
        final UUID uuid = ImportValues.uuid(raw).orElse(null);
        if (uuid == null) {
            final boolean nil = raw instanceof UUID given && given.equals(Uuids.ZERO)
                    || raw instanceof byte[] bytes && bytes.length == NIL_BYTES
                    && Arrays.equals(bytes, new byte[NIL_BYTES]);
            return nil ? Optional.of(ConsoleOperator.INSTANCE) : Optional.empty();
        }
        return Optional.of(ImportValues.operator(uuid, names.get(uuid)));
    }

    private static Table<Record> table(final SourceDatabase database, final String name) {
        return DSL.table(DSL.quotedName(database.prefix() + name));
    }

    private static <T> Field<T> column(final @Nullable String table, final String name,
            final Class<T> type) {
        return table == null
                ? DSL.field(DSL.quotedName(name), type)
                : DSL.field(DSL.quotedName(table, name), type);
    }
}
