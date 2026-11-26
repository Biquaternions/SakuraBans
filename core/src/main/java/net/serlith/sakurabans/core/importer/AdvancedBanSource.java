package net.serlith.sakurabans.core.importer;

import java.time.Instant;
import java.time.InstantSource;
import java.util.HashSet;
import java.util.LinkedHashMap;
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
import net.serlith.sakurabans.core.util.Uuids;
import org.jooq.Cursor;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Table;
import org.jooq.impl.DSL;

/**
 * AdvancedBan: {@code Punishments} (active) and {@code PunishmentHistory} (everything, also the
 * active ones, under other ids). History rows that equal an active row (same type, victim,
 * operator, reason and times) are the same punishment and are skipped.
 *
 * <p>The tables have no prefix and mixed-case names, which MySQL keeps and HSQLDB folds to upper
 * case, so they are written unquoted. AdvancedBan has no scopes, no revocation data and no
 * operator UUIDs.
 */
public final class AdvancedBanSource implements ImportSource {

    private static final String ACTIVE = "Punishments";
    private static final String HISTORY = "PunishmentHistory";

    private static final Field<Integer> ID = SourceDatabase.field("id", Integer.class);
    private static final Field<String> NAME = SourceDatabase.field("name", String.class);
    private static final Field<String> UUID_COLUMN = SourceDatabase.field("uuid", String.class);
    private static final Field<String> REASON = SourceDatabase.field("reason", String.class);
    private static final Field<String> OPERATOR =
            SourceDatabase.field("operator", String.class);
    private static final Field<String> TYPE =
            SourceDatabase.field("punishmentType", String.class);
    private static final Field<Long> START = SourceDatabase.field("start", Long.class);
    private static final Field<Long> END = SourceDatabase.field("end", Long.class);

    private final InstantSource clock;

    public AdvancedBanSource() {
        this(InstantSource.system());
    }

    AdvancedBanSource(final InstantSource clock) {
        this.clock = clock;
    }

    private enum Kind {
        BAN(PunishmentType.BAN, false),
        TEMP_BAN(PunishmentType.BAN, false),
        IP_BAN(PunishmentType.BAN, true),
        TEMP_IP_BAN(PunishmentType.BAN, true),
        MUTE(PunishmentType.MUTE, false),
        TEMP_MUTE(PunishmentType.MUTE, false),
        WARNING(PunishmentType.WARN, false),
        TEMP_WARNING(PunishmentType.WARN, false),
        KICK(PunishmentType.KICK, false);

        private final PunishmentType type;
        private final boolean address;

        Kind(final PunishmentType type, final boolean address) {
            this.type = type;
            this.address = address;
        }
    }

    /** What makes two rows the same punishment, whichever table and id they have. */
    private record Details(PunishmentType type, Victim victim, Operator operator, String reason,
            long start, long end) {
    }

    @Override
    public String id() {
        return "advancedban";
    }

    @Override
    public Optional<String> databaseSection() {
        return Optional.of(ImportSettings.ADVANCEDBAN);
    }

    @Override
    public Map<String, Long> survey(final SourceContext context) {
        final Map<String, Long> rows = new LinkedHashMap<>();
        rows.put(ACTIVE, (long) context.database().dsl().fetchCount(table(ACTIVE)));
        rows.put(HISTORY, (long) context.database().dsl().fetchCount(table(HISTORY)));
        return rows;
    }

    @Override
    public void read(final SourceContext context, final SourceReader reader) {
        final Set<Details> active = new HashSet<>();
        this.table(context, reader, ACTIVE, "punishments", active, false);
        this.table(context, reader, HISTORY, "history", active, true);
    }

    private static Table<Record> table(final String name) {
        return DSL.table(DSL.unquotedName(name));
    }

    private void table(final SourceContext context, final SourceReader reader,
            final String table, final String idTable, final Set<Details> active,
            final boolean history) {
        final SourceDatabase database = context.database();
        final Instant now = this.clock.instant();
        try (Cursor<? extends Record> rows = database.stream(database.dsl()
                .select(ID, NAME, UUID_COLUMN, REASON, OPERATOR, TYPE, START, END)
                .from(table(table)).orderBy(ID))) {
            for (final Record row : rows) {
                this.row(context, reader, row, table, idTable, active, history, now);
            }
        }
    }

    private void row(final SourceContext context, final SourceReader reader, final Record row,
            final String table, final String idTable, final Set<Details> active,
            final boolean history, final Instant now) {
        final String id = String.valueOf(row.get(ID));
        final String typeText = row.get(TYPE);
        if (typeText == null) {
            reader.failure(table, id, "no punishment type");
            return;
        }
        final Kind kind;
        try {
            kind = Kind.valueOf(typeText);
        } catch (final IllegalArgumentException e) {
            // AdvancedBan's NOTE has no counterpart here; anything else is a type of a newer
            // AdvancedBan, which the report lists.
            if (!typeText.equals("NOTE")) {
                reader.failure(table, id, "unknown punishment type " + typeText);
            }
            return;
        }
        final Long start = row.get(START);
        final Long end = row.get(END);
        if (start == null || start <= 0) {
            reader.failure(table, id, "no start time");
            return;
        }
        if (end == null || end == 0) {
            reader.failure(table, id, "no end time");
            return;
        }
        final String uuidText = row.get(UUID_COLUMN);
        final String operatorName = row.get(OPERATOR);
        if (uuidText == null || uuidText.isBlank()) {
            reader.failure(table, id, "no UUID, address or name");
            return;
        }
        if (operatorName == null || operatorName.isBlank()) {
            reader.failure(table, id, "no operator");
            return;
        }
        final Instant created = Instant.ofEpochMilli(start);
        // -1 is permanent. Kicks are instant, whatever their end column says.
        final Instant expires = kind.type == PunishmentType.KICK
                ? null : ImportValues.millis(end).orElse(null);
        if (expires != null && !expires.isAfter(created)) {
            reader.failure(table, id, "ends before it starts");
            return;
        }
        UUID uuid = null;
        String name = row.get(NAME);
        final Victim victim;
        if (kind.address) {
            final Optional<AddressVictim> address = ImportValues.address(uuidText)
                    .map(AddressVictim::of);
            if (address.isEmpty()) {
                reader.failure(table, id, "not an IP address: " + uuidText);
                return;
            }
            victim = address.get();
        } else {
            // A 32 digit UUID; offline-mode servers store the name in this column instead.
            uuid = uuidText.strip().length() == 32
                    ? ImportValues.uuid(uuidText).orElse(null) : null;
            if (uuid == null) {
                final Optional<String> byName = ImportValues.playerName(
                        name != null && name.equalsIgnoreCase(uuidText.strip())
                                ? name : uuidText);
                if (byName.isEmpty() || uuidText.strip().length() == 32) {
                    reader.failure(table, id, "not a UUID or player name: " + uuidText);
                    return;
                }
                name = byName.get();
                uuid = Uuids.offline(name);
            }
            victim = PlayerVictim.of(uuid);
        }
        final Operator operator = ImportValues.operator(null, operatorName);
        final Details details = new Details(kind.type, victim, operator,
                String.valueOf(row.get(REASON)), start, end);
        if (history && active.contains(details)) {
            return;
        }
        if (!history) {
            active.add(details);
        }
        if (uuid != null) {
            final Optional<String> valid = ImportValues.playerName(name);
            if (valid.isPresent()) {
                reader.player(ImportedPlayer.named(uuid, valid.get(), created));
            }
        }
        // Gone from the active table and still running: someone removed it, at an unknown time.
        // AdvancedBan keeps no revoker: the runner names the unknown one.
        final ImportedPunishment.End ended = history && kind.type != PunishmentType.KICK
                && (expires == null || expires.isAfter(now))
                ? new ImportedPunishment.End(EndKind.REVOKED, now.isAfter(created) ? now : created,
                        null, null)
                : null;
        reader.punishment(new ImportedPunishment(kind.type, victim, operator, created, expires,
                GlobalScope.INSTANCE, reader.reason(row.get(REASON)), Visibility.NORMAL, false,
                null, ended, context.externalId(idTable, id)));
    }
}
