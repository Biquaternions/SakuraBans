package net.serlith.sakurabans.core.importer;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.stream.JsonReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.serlith.sakurabans.api.model.AddressVictim;
import net.serlith.sakurabans.api.model.ConsoleOperator;
import net.serlith.sakurabans.api.model.GlobalScope;
import net.serlith.sakurabans.api.model.Operator;
import net.serlith.sakurabans.api.model.PlayerVictim;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.model.Victim;
import net.serlith.sakurabans.api.model.Visibility;
import net.serlith.sakurabans.core.model.ImportedPlayer;
import org.jspecify.annotations.Nullable;

/**
 * The vanilla server's {@code banned-players.json} and {@code banned-ips.json}. They only hold
 * active bans and have no ids, so the external id is the UUID or address plus the creation
 * time.
 */
public final class VanillaSource implements ImportSource {

    static final String PLAYERS_FILE = "banned-players.json";
    static final String IPS_FILE = "banned-ips.json";

    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss Z", Locale.ROOT);
    // What the server writes as "source" when no player or named operator banned.
    private static final Set<String> CONSOLE_SOURCES =
            Set.of("server", "console", "(unknown)", "unknown", "rcon");

    @Override
    public String id() {
        return "vanilla";
    }

    @Override
    public Optional<String> databaseSection() {
        return Optional.empty();
    }

    @Override
    public Map<String, Long> survey(final SourceContext context) throws IOException {
        final Map<String, Long> rows = new LinkedHashMap<>();
        rows.put(PLAYERS_FILE, this.count(context.settings().vanilla().bannedPlayers(),
                PLAYERS_FILE));
        rows.put(IPS_FILE, this.count(context.settings().vanilla().bannedIps(), IPS_FILE));
        return rows;
    }

    @Override
    public void read(final SourceContext context, final SourceReader reader)
            throws IOException {
        this.readFile(context, reader, context.settings().vanilla().bannedPlayers(), true);
        this.readFile(context, reader, context.settings().vanilla().bannedIps(), false);
    }

    private long count(final Path path, final String label) throws IOException {
        if (!Files.isRegularFile(path)) {
            return 0;
        }
        long count = 0;
        try (JsonReader json = this.open(path)) {
            json.beginArray();
            while (json.hasNext()) {
                json.skipValue();
                count++;
            }
            json.endArray();
        } catch (final JsonParseException | IllegalStateException e) {
            throw this.invalid(label, e);
        }
        return count;
    }

    private void readFile(final SourceContext context, final SourceReader reader,
            final Path path, final boolean players)
            throws IOException {
        if (!Files.isRegularFile(path)) {
            return;
        }
        final String label = players ? PLAYERS_FILE : IPS_FILE;
        try (JsonReader json = this.open(path)) {
            json.beginArray();
            int position = 0;
            while (json.hasNext()) {
                final JsonElement element = JsonParser.parseReader(json);
                position++;
                if (!element.isJsonObject()) {
                    reader.failure(label, "#" + position, "not a JSON object");
                    continue;
                }
                this.entry(context, reader, element.getAsJsonObject(), players, label, position);
            }
            json.endArray();
        } catch (final JsonParseException | IllegalStateException e) {
            throw this.invalid(label, e);
        }
    }

    private JsonReader open(final Path path) throws IOException {
        return new JsonReader(Files.newBufferedReader(path, StandardCharsets.UTF_8));
    }

    private IOException invalid(final String label, final RuntimeException cause) {
        return new IOException(label + " is not a valid JSON array of bans: "
                + cause.getMessage(), cause);
    }

    private void entry(final SourceContext context, final SourceReader reader,
            final JsonObject entry, final boolean players, final String label,
            final int position) {
        final String key = players ? text(entry, "uuid") : text(entry, "ip");
        final String id = key == null ? "#" + position : key;
        UUID uuid = null;
        final Victim victim;
        if (players) {
            uuid = ImportValues.uuid(key).orElse(null);
            if (uuid == null) {
                reader.failure(label, id, "not a UUID: " + key);
                return;
            }
            victim = PlayerVictim.of(uuid);
        } else {
            final Optional<AddressVictim> address = ImportValues.address(key)
                    .map(AddressVictim::of);
            if (address.isEmpty()) {
                reader.failure(label, id, "not an IP address: " + key);
                return;
            }
            victim = address.get();
        }
        final Instant created = date(text(entry, "created")).orElse(null);
        if (created == null) {
            reader.failure(label, id, "no valid creation time: " + text(entry, "created"));
            return;
        }
        final String expiresText = text(entry, "expires");
        Instant expires = null;
        if (expiresText != null && !expiresText.strip().equalsIgnoreCase("forever")) {
            expires = date(expiresText).orElse(null);
            if (expires == null) {
                reader.failure(label, id, "not a valid expiry: " + expiresText);
                return;
            }
        }
        if (uuid != null) {
            final String name = text(entry, "name");
            if (ImportValues.playerName(name).isPresent()) {
                reader.player(ImportedPlayer.named(uuid, name.strip(), created));
            }
        }
        reader.punishment(new ImportedPunishment(PunishmentType.BAN, victim,
                operator(text(entry, "source")), created, expires, GlobalScope.INSTANCE,
                reader.reason(text(entry, "reason")), Visibility.NORMAL, false, null, null,
                context.externalId(players ? "players" : "ips",
                        id + ":" + created.getEpochSecond())));
    }

    private static Operator operator(final @Nullable String source) {
        if (source == null || CONSOLE_SOURCES.contains(source.strip().toLowerCase(Locale.ROOT))) {
            return ConsoleOperator.INSTANCE;
        }
        return ImportValues.operator(null, source);
    }

    private static Optional<Instant> date(final @Nullable String text) {
        if (text == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(OffsetDateTime.parse(text.strip(), DATE).toInstant());
        } catch (final DateTimeParseException e) {
            return Optional.empty();
        }
    }

    private static @Nullable String text(final JsonObject object, final String key) {
        return object.get(key) instanceof JsonPrimitive primitive ? primitive.getAsString() : null;
    }

}
