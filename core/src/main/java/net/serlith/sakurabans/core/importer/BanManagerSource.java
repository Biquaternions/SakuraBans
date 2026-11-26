package net.serlith.sakurabans.core.importer;

import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.serlith.sakurabans.api.model.AddressVictim;
import net.serlith.sakurabans.api.model.EndKind;
import net.serlith.sakurabans.api.model.GlobalScope;
import net.serlith.sakurabans.api.model.Operator;
import net.serlith.sakurabans.api.model.PlayerVictim;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.model.Victim;
import net.serlith.sakurabans.api.model.Visibility;
import net.serlith.sakurabans.core.configuration.settings.ImportSettings;
import net.serlith.sakurabans.core.model.ImportedPlayer;
import org.jooq.Cursor;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.SelectField;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.jspecify.annotations.Nullable;

/**
 * BanManager (default table prefix {@code bm_}), read like LibertyBans' importer does: the
 * active tables, the {@code *_records} tables of ended punishments, and {@code players} for
 * names and addresses. Names are quoted exactly as BanManager creates them.
 *
 * <p>The console is the player row with the oldest {@code lastSeen}. Times are epoch seconds
 * and an expiry of 0 is permanent. Not imported: name bans ({@code name_bans}), address range
 * bans ({@code ip_range_bans}), notes, reports, rollbacks and {@code player_history}.
 */
public final class BanManagerSource implements ImportSource {

    private enum Spec {
        PLAYER_BANS("player_bans", PunishmentType.BAN, false, true),
        PLAYER_BAN_RECORDS("player_ban_records", PunishmentType.BAN, false, false),
        IP_BANS("ip_bans", PunishmentType.BAN, true, true),
        IP_BAN_RECORDS("ip_ban_records", PunishmentType.BAN, true, false),
        PLAYER_MUTES("player_mutes", PunishmentType.MUTE, false, true),
        PLAYER_MUTE_RECORDS("player_mute_records", PunishmentType.MUTE, false, false),
        IP_MUTES("ip_mutes", PunishmentType.MUTE, true, true),
        IP_MUTE_RECORDS("ip_mute_records", PunishmentType.MUTE, true, false),
        PLAYER_WARNINGS("player_warnings", PunishmentType.WARN, false, true),
        PLAYER_KICKS("player_kicks", PunishmentType.KICK, false, true);

        private final String table;
        private final PunishmentType type;
        private final boolean address;
        private final boolean active;

        Spec(final String table, final PunishmentType type, final boolean address,
                final boolean active) {
            this.table = table;
            this.type = type;
            this.address = address;
            this.active = active;
        }
    }

    private static final Field<Integer> ID = DSL.field(DSL.name("t", "id"), Integer.class)
            .as("row_id");
    private static final Field<Object> VICTIM_ID = DSL.field(DSL.name("t", "player_id"))
            .as("victim_id");
    private static final Field<Object> VICTIM_IP = DSL.field(DSL.name("t", "ip"))
            .as("victim_ip");
    private static final Field<String> REASON = DSL.field(DSL.name("t", "reason"),
            String.class).as("row_reason");
    private static final Field<Long> CREATED = DSL.field(DSL.name("t", "created"), Long.class)
            .as("row_created");
    private static final Field<Long> EXPIRES = DSL.field(DSL.name("t", "expires"), Long.class)
            .as("row_expires");
    private static final Field<Long> EXPIRED = DSL.field(DSL.name("t", "expired"), Long.class)
            .as("row_expired");
    private static final Field<Long> PAST_CREATED = DSL.field(DSL.name("t", "pastCreated"),
            Long.class).as("row_past_created");
    private static final Field<String> CREATED_REASON = DSL.field(
            DSL.name("t", "createdReason"), String.class).as("row_created_reason");
    private static final Field<Integer> SOFT = DSL.field(DSL.name("t", "soft"), Integer.class)
            .as("row_soft");
    private static final Field<Integer> SILENT = DSL.field(DSL.name("t", "silent"),
            Integer.class).as("row_silent");
    // The player who issued it: actor_id, or pastActor_id in the records.
    private static final Field<Object> ACTOR_ID = DSL.field(DSL.name("t", "actor_id"))
            .as("actor_id");
    private static final Field<Object> PAST_ACTOR_ID = DSL.field(DSL.name("t", "pastActor_id"))
            .as("past_actor_id");
    private static final Field<String> ACTOR_NAME = DSL.field(DSL.name("a", "name"),
            String.class).as("actor_name");
    private static final Field<String> PAST_ACTOR_NAME = DSL.field(DSL.name("p", "name"),
            String.class).as("past_actor_name");

    private static final Field<Object> PLAYER_ID = DSL.field(DSL.name("id"));
    private static final Field<String> PLAYER_NAME = DSL.field(DSL.name("name"), String.class);
    private static final Field<Object> PLAYER_IP = DSL.field(DSL.name("ip"));
    private static final Field<Long> LAST_SEEN = DSL.field(DSL.name("lastSeen"), Long.class);

    @Override
    public String id() {
        return "banmanager";
    }

    @Override
    public Optional<String> databaseSection() {
        return Optional.of(ImportSettings.BANMANAGER);
    }

    @Override
    public Map<String, Long> survey(final SourceContext context) {
        final SourceDatabase database = context.database();
        if (!database.exists("players")) {
            throw new IllegalStateException("no " + database.prefix() + "players table");
        }
        final Map<String, Long> rows = new LinkedHashMap<>();
        rows.put("players", (long) database.dsl().fetchCount(table(database, "players")));
        for (final Spec spec : Spec.values()) {
            if (database.exists(spec.table)) {
                rows.put(spec.table, (long) database.dsl().fetchCount(
                        table(database, spec.table)));
            }
        }
        return rows;
    }

    @Override
    public void read(final SourceContext context, final SourceReader reader) {
        final SourceDatabase database = context.database();
        final UUID console = this.console(database);
        this.players(database, reader, console);
        for (final Spec spec : Spec.values()) {
            if (database.exists(spec.table)) {
                this.readTable(context, reader, spec, console);
            }
        }
    }

    private static Table<Record> table(final SourceDatabase database, final String name) {
        return DSL.table(DSL.quotedName(database.prefix() + name));
    }

    // BanManager's console is the first player row it ever stored.
    private @Nullable UUID console(final SourceDatabase database) {
        final Record first = database.dsl().select(PLAYER_ID).from(table(database, "players"))
                .orderBy(LAST_SEEN.asc()).limit(1).fetchOne();
        return first == null ? null : ImportValues.uuid(first.get(PLAYER_ID)).orElse(null);
    }

    private void players(final SourceDatabase database, final SourceReader reader,
            final @Nullable UUID console) {
        try (Cursor<? extends Record> rows = database.stream(database.dsl()
                .select(PLAYER_ID, PLAYER_NAME, PLAYER_IP, LAST_SEEN)
                .from(table(database, "players")).orderBy(LAST_SEEN.asc()))) {
            for (final Record row : rows) {
                final UUID uuid = ImportValues.uuid(row.get(PLAYER_ID)).orElse(null);
                if (uuid == null || uuid.equals(console)) {
                    continue;
                }
                final String name = ImportValues.playerName(row.get(PLAYER_NAME)).orElse(null);
                final InetAddress address = address(row.get(PLAYER_IP)).orElse(null);
                if (name == null && address == null) {
                    continue;
                }
                final Long seen = row.get(LAST_SEEN);
                final Instant at = ImportValues.seconds(seen).orElse(Instant.EPOCH);
                reader.player(new ImportedPlayer(uuid, name, address, at, at));
            }
        }
    }

    /** An address column: bytes (4 or 16), or the integer older BanManagers stored. */
    private static Optional<InetAddress> address(final @Nullable Object value) {
        if (value instanceof Number number) {
            return ImportValues.address(ByteBuffer.allocate(4).putInt(number.intValue())
                    .array());
        }
        return ImportValues.address(value);
    }

    private Set<String> columns(final SourceDatabase database, final String table) {
        final String wanted = database.prefix() + table;
        return database.dsl().connectionResult(connection -> {
            final Set<String> found = new HashSet<>();
            try (ResultSet columns = connection.getMetaData().getColumns(connection.getCatalog(),
                    null, null, null)) {
                while (columns.next()) {
                    if (wanted.equalsIgnoreCase(columns.getString("TABLE_NAME"))) {
                        found.add(columns.getString("COLUMN_NAME").toLowerCase(Locale.ROOT));
                    }
                }
            }
            return found;
        });
    }

    private void readTable(final SourceContext context, final SourceReader reader,
            final Spec spec, final @Nullable UUID console) {
        final SourceDatabase database = context.database();
        final Set<String> columns = this.columns(database, spec.table);
        final boolean soft = columns.contains("soft");
        final boolean silent = columns.contains("silent");
        final List<SelectField<?>> select = new ArrayList<>(List.of(ID, REASON,
                spec.address ? VICTIM_IP : VICTIM_ID, ACTOR_NAME));
        select.add(spec.active ? ACTOR_ID : PAST_ACTOR_ID);
        if (spec.active) {
            select.add(CREATED);
            if (spec.type != PunishmentType.KICK) {
                select.add(EXPIRES);
            }
        } else {
            select.addAll(List.of(PAST_CREATED, EXPIRED, CREATED, CREATED_REASON,
                    PAST_ACTOR_NAME, ACTOR_ID));
        }
        if (soft) {
            select.add(SOFT);
        }
        if (silent) {
            select.add(SILENT);
        }
        // The operator is actor_id of an active row; of a record, the remover is actor_id (joined
        // as "a") and the original operator pastActor_id (joined as "p").
        final Table<Record> actors = table(database, spec.table).as("t")
                .leftJoin(table(database, "players").as("a"))
                .on(DSL.field(DSL.name("a", "id")).eq(DSL.field(DSL.name("t", "actor_id"))));
        final Table<Record> from = spec.active ? actors
                : actors.leftJoin(table(database, "players").as("p"))
                        .on(DSL.field(DSL.name("p", "id"))
                                .eq(DSL.field(DSL.name("t", "pastActor_id"))));
        try (Cursor<? extends Record> rows = database.stream(database.dsl().select(select)
                .from(from).orderBy(ID))) {
            for (final Record row : rows) {
                this.row(context, reader, spec, row, console, soft, silent);
            }
        }
    }

    private void row(final SourceContext context, final SourceReader reader, final Spec spec,
            final Record row, final @Nullable UUID console, final boolean soft,
            final boolean silent) {
        final String id = String.valueOf(row.get(ID));
        final Victim victim;
        if (spec.address) {
            final Optional<InetAddress> address = address(row.get(VICTIM_IP));
            if (address.isEmpty()) {
                reader.failure(spec.table, id, "no valid IP address");
                return;
            }
            victim = AddressVictim.of(address.get());
        } else {
            final Optional<UUID> uuid = ImportValues.uuid(row.get(VICTIM_ID));
            if (uuid.isEmpty()) {
                reader.failure(spec.table, id, "no valid player id");
                return;
            }
            victim = PlayerVictim.of(uuid.get());
        }
        final Instant created = ImportValues.seconds(row.get(spec.active
                ? CREATED : PAST_CREATED)).orElse(null);
        if (created == null) {
            reader.failure(spec.table, id, "no creation time");
            return;
        }
        final Operator operator = this.operator(row.get(spec.active ? ACTOR_ID : PAST_ACTOR_ID),
                row.get(spec.active ? ACTOR_NAME : PAST_ACTOR_NAME), console);
        final Instant expires = spec.type == PunishmentType.KICK ? null
                : ImportValues.seconds(row.get(spec.active ? EXPIRES : EXPIRED)).orElse(null);
        reader.punishment(new ImportedPunishment(spec.type, victim, operator, created, expires,
                GlobalScope.INSTANCE, reader.reason(row.get(REASON)),
                silent && row.get(SILENT) != null && row.get(SILENT) != 0
                        ? Visibility.SILENT : Visibility.NORMAL,
                soft && spec.type == PunishmentType.MUTE && row.get(SOFT) != null
                        && row.get(SOFT) != 0,
                null, spec.active ? null : this.ended(reader, row, expires, created, console),
                context.externalId(spec.table, id)));
    }

    // A record is a punishment that ended: expired when it was archived at or after its expiry
    // (BanManager's own sweep), otherwise removed by the actor of the record.
    private ImportedPunishment.End ended(final SourceReader reader, final Record row,
            final @Nullable Instant expires, final Instant created,
            final @Nullable UUID console) {
        final Instant removed = ImportValues.seconds(row.get(CREATED)).orElse(created);
        if (expires != null && !removed.isBefore(expires)) {
            return new ImportedPunishment.End(EndKind.EXPIRED, expires, null, null);
        }
        final String why = row.get(CREATED_REASON);
        return new ImportedPunishment.End(EndKind.REVOKED,
                removed.isBefore(created) ? created : removed,
                this.operator(row.get(ACTOR_ID), row.get(ACTOR_NAME), console),
                why == null || why.isBlank() ? null : reader.reason(why));
    }

    private Operator operator(final @Nullable Object actorId, final @Nullable String name,
            final @Nullable UUID console) {
        final UUID uuid = ImportValues.uuid(actorId).orElse(null);
        return ImportValues.operator(uuid, name, console);
    }
}
