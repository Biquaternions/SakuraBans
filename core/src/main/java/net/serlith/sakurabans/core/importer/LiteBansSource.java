package net.serlith.sakurabans.core.importer;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.serlith.sakurabans.api.imports.ImportedPunishment;
import net.serlith.sakurabans.api.model.AddressRangeVictim;
import net.serlith.sakurabans.api.model.AddressVictim;
import net.serlith.sakurabans.api.model.CompositeVictim;
import net.serlith.sakurabans.api.model.ConsoleOperator;
import net.serlith.sakurabans.api.model.EndKind;
import net.serlith.sakurabans.api.model.Lengths;
import net.serlith.sakurabans.api.model.Operator;
import net.serlith.sakurabans.api.model.PlayerVictim;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.model.Scope;
import net.serlith.sakurabans.api.model.Victim;
import net.serlith.sakurabans.api.model.Visibility;
import net.serlith.sakurabans.core.configuration.settings.ImportSettings;
import net.serlith.sakurabans.core.model.ImportedPlayer;
import org.jooq.Cursor;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.jspecify.annotations.Nullable;

/**
 * LiteBans ({@code bans}, {@code mutes}, {@code warnings}, {@code kicks}, {@code history}),
 * schema and quirks as LibertyBans' importer documents them. Times are epoch milliseconds,
 * {@code removed_by_date} and {@code history.date} are SQL timestamps.
 */
public final class LiteBansSource implements ImportSource {

    public static final String ID = "litebans";

    static final String HISTORY = "history";

    private static final List<Kind> KINDS = List.of(
            new Kind("bans", PunishmentType.BAN),
            new Kind("mutes", PunishmentType.MUTE),
            new Kind("warnings", PunishmentType.WARN),
            new Kind("kicks", PunishmentType.KICK));

    // LiteBans' stand-ins for a UUID or address it doesn't know; "#" is the console's address.
    private static final List<String> UNKNOWN = List.of("#offline#", "#undefined#", "#");
    private static final String CONSOLE_UUID = "CONSOLE";
    // removed_by_name of a punishment LiteBans ended itself when it ran out.
    private static final String EXPIRED = "#expired";
    // Who revoked a punishment LiteBans recorded no remover for. A stored revocation needs an
    // operator (SakuraPunishmentEnd), so it is attributed to LiteBans itself.

    private static final Field<Object> ID_COLUMN = SourceDatabase.field("id");

    private record Kind(String table, PunishmentType type) {
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public Optional<String> databaseSection() {
        return Optional.of(ImportSettings.LITEBANS);
    }

    @Override
    public Map<String, Long> survey(final SourceContext context) {
        final SourceDatabase database = context.database();
        final Map<String, Long> rows = new LinkedHashMap<>();
        for (final Kind kind : KINDS) {
            rows.put(kind.table(), database.count(kind.table()));
        }
        rows.put(HISTORY, database.count(HISTORY));
        return rows;
    }

    @Override
    public void read(final SourceContext context, final SourceReader reader) {
        if (this.readHistory(context, reader, false)) {
            this.readHistory(context, reader, true);
        }
        for (final Kind kind : KINDS) {
            this.readPunishments(context, reader, kind);
        }
    }

    // Names (with their address) on the first pass, rows without a usable name on the second.
    // Returns whether the first pass saw rows for the second.
    private boolean readHistory(final SourceContext context, final SourceReader reader,
            final boolean addressOnly) {
        final SourceDatabase database = context.database();
        boolean deferred = false;
        try (Cursor<Record> rows = database.stream(database.dsl().select(DSL.asterisk())
                .from(database.table(HISTORY)).orderBy(ID_COLUMN))) {
            final Columns columns = new Columns(rows.fields());
            for (final Record row : rows) {
                final String id = String.valueOf(columns.get(row, "id"));
                final String uuidText = columns.text(row, "uuid");
                if (uuidText == null || isPlaceholder(uuidText)
                        || uuidText.strip().equalsIgnoreCase(CONSOLE_UUID)) {
                    continue;
                }
                final Optional<String> name = ImportValues.playerName(columns.text(row, "name"));
                if (name.isEmpty() != addressOnly) {
                    deferred |= name.isEmpty();
                    continue;
                }
                final Optional<UUID> uuid = ImportValues.uuid(uuidText);
                if (uuid.isEmpty()) {
                    reader.failure(HISTORY, id, "not a UUID: " + uuidText);
                    continue;
                }
                final Optional<Instant> seen = instant(columns.get(row, "date"));
                if (seen.isEmpty()) {
                    reader.failure(HISTORY, id, "no date");
                    continue;
                }
                final String ip = columns.text(row, "ip");
                final InetAddress address = ip == null || isPlaceholder(ip) ? null
                        : ImportValues.address(ip).orElse(null);
                if (name.isEmpty() && address == null) {
                    reader.failure(HISTORY, id, "neither a name nor an address");
                    continue;
                }
                reader.player(new ImportedPlayer(uuid.get(), name.orElse(null), address,
                        seen.get(), seen.get()));
            }
        }
        return deferred;
    }

    private void readPunishments(final SourceContext context, final SourceReader reader,
            final Kind kind) {
        final SourceDatabase database = context.database();
        try (Cursor<Record> rows = database.stream(database.dsl().select(DSL.asterisk())
                .from(database.table(kind.table())).orderBy(ID_COLUMN))) {
            final Columns columns = new Columns(rows.fields());
            for (final Record row : rows) {
                final String id = String.valueOf(columns.get(row, "id"));
                try {
                    this.convert(context, reader, kind, columns, row, id);
                } catch (final Rejected e) {
                    reader.failure(kind.table(), id, e.getMessage());
                } catch (final IllegalArgumentException e) {
                    // The builder's validation (an expiry before creation and the like).
                    reader.failure(kind.table(), id, String.valueOf(e.getMessage()));
                }
            }
        }
    }

    private void convert(final SourceContext context, final SourceReader reader,
            final Kind kind, final Columns columns, final Record row, final String id)
            throws Rejected {
        final Victim victim = victim(columns, row);
        final Instant createdAt = ImportValues.millis(columns.number(row, "time"))
                .orElseThrow(() -> new Rejected("no time"));
        final String scopeName = columns.text(row, "server_scope");
        final Scope scope = ImportValues.scope(scopeName)
                .orElseThrow(() -> new Rejected("not a server name: " + scopeName));
        final ImportedPunishment.Builder builder = ImportedPunishment.builder(kind.type(),
                        victim, operator(columns.text(row, "banned_by_uuid"),
                                columns.text(row, "banned_by_name")), createdAt)
                .scope(scope)
                .reason(reader.reason(columns.text(row, "reason")))
                .visibility(flag(columns.get(row, "silent"))
                        ? Visibility.SILENT : Visibility.NORMAL)
                .externalId(context.externalId(kind.table(), id));
        final String origin = columns.text(row, "server_origin");
        if (origin != null && !origin.isBlank()) {
            builder.originServer(reader.fit(origin.strip(), Lengths.MAX_IDENTIFIER));
        }
        if (!kind.type().instant()) {
            // Sometimes 0, sometimes -1 for permanent.
            final Optional<Instant> until = ImportValues.millis(columns.number(row, "until"));
            builder.expiresAt(until.orElse(null));
            if (!flag(columns.get(row, "active"))) {
                end(reader, builder, columns, row, createdAt, until.orElse(null));
            }
        }
        reader.punishment(builder.build());
    }

    private static Victim victim(final Columns columns, final Record row) throws Rejected {
        final String uuidText = columns.text(row, "uuid");
        final UUID uuid = uuidText == null || isPlaceholder(uuidText) ? null
                : ImportValues.uuid(uuidText).orElse(null);
        if (uuidText != null && !isPlaceholder(uuidText) && uuid == null) {
            throw new Rejected("not a UUID: " + uuidText);
        }
        if (!flag(columns.get(row, "ipban"))) {
            // The ip column of a player punishment is only the address the player last had.
            if (uuid == null) {
                throw new Rejected("a player punishment without a UUID");
            }
            return PlayerVictim.of(uuid);
        }
        final String ip = columns.text(row, "ip");
        final boolean knownIp = ip != null && !isPlaceholder(ip);
        if (flag(columns.get(row, "ipban_wildcard"))) {
            if (!knownIp) {
                throw new Rejected("a wildcard IP ban without a pattern");
            }
            return range(ip.strip()).orElseThrow(() -> new Rejected(
                    "a wildcard IP ban that isn't a CIDR range: " + ip));
        }
        final InetAddress address = knownIp ? ImportValues.address(ip).orElse(null) : null;
        if (address != null) {
            // LiteBans enforces an IP ban on the address and on the account it names.
            return uuid == null ? AddressVictim.of(address) : CompositeVictim.of(uuid, address);
        }
        if (uuid != null && !knownIp) {
            // An IP ban of a player whose address LiteBans didn't know yet: the account part.
            return PlayerVictim.of(uuid);
        }
        throw new Rejected(knownIp
                ? "an IP ban of something that isn't an address: " + ip
                : "an IP ban with neither a UUID nor an address");
    }

    private static void end(final SourceReader reader, final ImportedPunishment.Builder builder,
            final Columns columns, final Record row, final Instant createdAt,
            final @Nullable Instant until) {
        final String removerUuid = columns.text(row, "removed_by_uuid");
        final String removerName = columns.text(row, "removed_by_name");
        final Instant removedAt = instant(columns.get(row, "removed_by_date"))
                .filter(at -> !at.isBefore(createdAt)).orElse(null);
        final boolean expired = isExpired(removerUuid) || isExpired(removerName);
        final boolean known = present(removerUuid) || present(removerName);
        if (until != null && (expired || !known && (removedAt == null
                || !removedAt.isBefore(until)))) {
            builder.ended(EndKind.EXPIRED, until.isBefore(createdAt) ? createdAt : until, null,
                    null);
            return;
        }
        final Instant at = removedAt == null ? createdAt : removedAt;
        if (expired) {
            // A permanent punishment LiteBans still marked as run out.
            builder.ended(EndKind.EXPIRED, at, null, null);
            return;
        }
        final String reason = columns.text(row, "removed_by_reason");
        builder.ended(EndKind.REVOKED, at,
                known ? operator(removerUuid, removerName) : null,
                reason == null || reason.isBlank() ? null : reader.reason(reason));
    }

    private static Operator operator(final @Nullable String uuidText,
            final @Nullable String name) {
        if (uuidText != null && uuidText.strip().equalsIgnoreCase(CONSOLE_UUID)) {
            return ConsoleOperator.INSTANCE;
        }
        // LiteBans sometimes writes a name into banned_by_uuid; then only the name is known.
        final UUID uuid = uuidText == null ? null : ImportValues.uuid(uuidText).orElse(null);
        final String shown = present(name) ? name
                : uuid == null && present(uuidText) && !isPlaceholder(uuidText) ? uuidText
                : null;
        return ImportValues.operator(uuid, shown);
    }

    /**
     * The range of a LiteBans wildcard pattern: CIDR ({@code 10.0.0.0/16}) or IPv4 with
     * trailing {@code *} octets ({@code 10.0.*.*}, {@code 10.0.*}). Anything else (a star in
     * the middle, all stars) is empty.
     */
    static Optional<AddressRangeVictim> range(final String pattern) {
        try {
            final int slash = pattern.indexOf('/');
            if (slash >= 0) {
                final Optional<InetAddress> network =
                        ImportValues.address(pattern.substring(0, slash));
                final int prefix = Integer.parseInt(pattern.substring(slash + 1));
                return network.map(address -> AddressRangeVictim.of(address, prefix));
            }
            final String[] octets = pattern.split("\\.", -1);
            if (octets.length < 2 || octets.length > 4) {
                return Optional.empty();
            }
            int fixed = 0;
            while (fixed < octets.length && !octets[fixed].equals("*")) {
                fixed++;
            }
            if (fixed == 0 || fixed == octets.length) {
                return Optional.empty();
            }
            final int prefix = fixed * Byte.SIZE;
            for (int i = fixed; i < octets.length; i++) {
                if (!octets[i].equals("*")) {
                    return Optional.empty();
                }
            }
            final StringBuilder network = new StringBuilder();
            for (int i = 0; i < 4; i++) {
                network.append(i == 0 ? "" : ".").append(i < fixed ? octets[i] : "0");
            }
            return ImportValues.address(network.toString())
                    .filter(Inet4Address.class::isInstance)
                    .map(address -> AddressRangeVictim.of(address, prefix));
        } catch (final IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /** A flag column as the drivers return it: boolean, number, bit bytes or text. */
    static boolean flag(final @Nullable Object value) {
        return switch (value) {
            case null -> false;
            case Boolean bool -> bool;
            case Number number -> number.longValue() != 0;
            case byte[] bytes -> {
                for (final byte b : bytes) {
                    if (b != 0) {
                        yield true;
                    }
                }
                yield false;
            }
            case String text -> text.strip().equals("1") || text.strip().equalsIgnoreCase("true")
                    || text.strip().equalsIgnoreCase("b'1'");
            default -> false;
        };
    }

    private static Optional<Instant> instant(final @Nullable Object value) {
        return switch (value) {
            case null -> Optional.empty();
            case Timestamp timestamp -> Optional.of(timestamp.toInstant());
            case LocalDateTime local -> Optional.of(local.atZone(ZoneId.systemDefault())
                    .toInstant());
            case OffsetDateTime offset -> Optional.of(offset.toInstant());
            case Instant instant -> Optional.of(instant);
            case java.util.Date date -> Optional.of(date.toInstant());
            case Number millis -> ImportValues.millis(millis);
            default -> Optional.empty();
        };
    }

    private static boolean isPlaceholder(final String value) {
        return value.isBlank() || UNKNOWN.contains(value.strip().toLowerCase(Locale.ROOT));
    }

    private static boolean isExpired(final @Nullable String value) {
        return value != null && value.strip().equalsIgnoreCase(EXPIRED);
    }

    private static boolean present(final @Nullable String value) {
        return value != null && !value.isBlank();
    }

    // A row that can't be imported, and why.
    private static final class Rejected extends Exception {

        Rejected(final String problem) {
            super(problem, null, false, false);
        }
    }

    // Columns by lower-case name: databases differ in case, and older LiteBans versions lack
    // some (removed_by_reason, server_origin, template).
    private static final class Columns {

        private final Map<String, Integer> indexes = new HashMap<>();

        Columns(final Field<?>[] fields) {
            for (int i = 0; i < fields.length; i++) {
                this.indexes.putIfAbsent(fields[i].getName().toLowerCase(Locale.ROOT), i);
            }
        }

        @Nullable Object get(final Record row, final String name) {
            final Integer index = this.indexes.get(name);
            return index == null ? null : row.get(index);
        }

        @Nullable String text(final Record row, final String name) {
            final Object value = this.get(row, name);
            return value == null ? null : value.toString();
        }

        @Nullable Number number(final Record row, final String name) {
            return switch (this.get(row, name)) {
                case Number number -> number;
                case String text -> {
                    try {
                        yield Long.parseLong(text.strip());
                    } catch (final NumberFormatException e) {
                        yield null;
                    }
                }
                case null, default -> null;
            };
        }
    }
}
