package net.serlith.sakurabans.core.importer;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import net.serlith.sakurabans.api.model.AddressVictim;
import net.serlith.sakurabans.api.model.CompositeVictim;
import net.serlith.sakurabans.api.model.ConsoleOperator;
import net.serlith.sakurabans.api.model.CustomOperator;
import net.serlith.sakurabans.api.model.GlobalScope;
import net.serlith.sakurabans.api.model.Lengths;
import net.serlith.sakurabans.api.model.Operator;
import net.serlith.sakurabans.api.model.PlayerOperator;
import net.serlith.sakurabans.api.model.PlayerVictim;
import net.serlith.sakurabans.api.model.Scope;
import net.serlith.sakurabans.api.model.ServerScope;
import net.serlith.sakurabans.api.model.Victim;
import net.serlith.sakurabans.api.validation.Validator;
import net.serlith.sakurabans.core.util.InetAddresses;
import net.serlith.sakurabans.core.util.Strings;
import net.serlith.sakurabans.core.util.Uuids;
import org.jspecify.annotations.Nullable;

public final class ImportValues {

    private static final Pattern HEX_COLOUR = Pattern.compile("[&§]#[0-9a-fA-F]{6}");
    private static final Pattern SPIGOT_HEX_COLOUR =
            Pattern.compile("[&§][xX]([&§][0-9a-fA-F]){6}");
    private static final Pattern LEGACY_CODE = Pattern.compile("[&§][0-9a-fA-Fk-oK-OrRxX]");
    private static final String CONSOLE = "console";

    public static final Operator UNKNOWN_REVOKER = CustomOperator.of("unknown", null);

    private ImportValues() {
    }

    public static Optional<UUID> uuid(final @Nullable Object value) {
        final UUID uuid = switch (value) {
            case null -> null;
            case UUID given -> given;
            case byte[] bytes when bytes.length == 16 -> {
                final ByteBuffer buffer = ByteBuffer.wrap(bytes);
                yield new UUID(buffer.getLong(), buffer.getLong());
            }
            case String text -> Uuids.parse(text.strip()).orElse(null);
            default -> null;
        };
        return uuid == null || uuid.equals(Uuids.ZERO) ? Optional.empty() : Optional.of(uuid);
    }

    public static Optional<InetAddress> address(final @Nullable Object value) {
        try {
            return switch (value) {
                case null -> Optional.empty();
                case InetAddress address -> Optional.of(InetAddresses.normalize(address));
                case byte[] bytes when bytes.length == 4 || bytes.length == 16 ->
                        Optional.of(InetAddresses.normalize(InetAddress.getByAddress(bytes)));
                case String text -> InetAddresses.parseLiteral(strip(text.strip()))
                        .map(InetAddresses::normalize);
                default -> Optional.empty();
            };
        } catch (final UnknownHostException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    // "/1.2.3.4" (InetAddress#toString without host) is a common way to store addresses.
    private static String strip(final String text) {
        return text.startsWith("/") ? text.substring(1) : text;
    }

    public static Optional<Instant> seconds(final long epochSeconds) {
        return epochSeconds <= 0
                ? Optional.empty()
                : Optional.of(Instant.ofEpochSecond(epochSeconds));
    }

    public static Optional<Instant> seconds(final @Nullable Number epochSeconds) {
        return epochSeconds == null ? Optional.empty() : seconds(epochSeconds.longValue());
    }

    public static Optional<Instant> millis(final long epochMillis) {
        return epochMillis <= 0
                ? Optional.empty()
                : Optional.of(Instant.ofEpochMilli(epochMillis));
    }

    public static Optional<Instant> millis(final @Nullable Number epochMillis) {
        return epochMillis == null ? Optional.empty() : millis(epochMillis.longValue());
    }

    public static String plain(final String text) {
        String plain = HEX_COLOUR.matcher(text).replaceAll("");
        plain = SPIGOT_HEX_COLOUR.matcher(plain).replaceAll("");
        return LEGACY_CODE.matcher(plain).replaceAll("");
    }

    public static String truncate(final String text, final int maxLength) {
        return Strings.truncate(text, maxLength);
    }

    public static Optional<Scope> scope(final @Nullable String server) {
        if (server == null || server.isBlank() || server.strip().equals("*")
                || server.strip().equalsIgnoreCase("global")) {
            return Optional.of(GlobalScope.INSTANCE);
        }
        return Validator.normalizeIdentifier(server.strip()).map(ServerScope::of);
    }

    public static boolean isConsole(final @Nullable UUID uuid, final @Nullable String name,
            final UUID... consoleUuids) {
        if (uuid != null) {
            if (uuid.equals(Uuids.ZERO)) {
                return true;
            }
            for (final UUID console : consoleUuids) {
                if (uuid.equals(console)) {
                    return true;
                }
            }
            return false;
        }
        return name == null || name.isBlank() || name.strip().equalsIgnoreCase(CONSOLE);
    }

    public static Operator operator(final @Nullable UUID uuid, final @Nullable String name,
            final UUID... consoleUuids) {
        if (isConsole(uuid, name, consoleUuids)) {
            return ConsoleOperator.INSTANCE;
        }
        final String stripped = name == null ? "" : name.strip();
        if (uuid != null && !stripped.isEmpty() && stripped.length() <= Lengths.MAX_PLAYER_NAME) {
            return PlayerOperator.of(uuid, stripped);
        }
        final String shown = stripped.isEmpty() && uuid != null ? uuid.toString()
                : truncate(stripped, Lengths.MAX_OPERATOR_NAME);
        return CustomOperator.of(shown, uuid);
    }

    public static Optional<Victim> victim(final @Nullable UUID uuid,
            final @Nullable InetAddress address) {
        if (uuid != null && address != null) {
            return Optional.of(CompositeVictim.of(uuid, address));
        }
        if (uuid != null) {
            return Optional.of(PlayerVictim.of(uuid));
        }
        return address == null ? Optional.empty() : Optional.of(AddressVictim.of(address));
    }

    public static Optional<String> playerName(final @Nullable String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        final String stripped = name.strip();
        return stripped.length() <= Lengths.MAX_PLAYER_NAME
                ? Optional.of(stripped) : Optional.empty();
    }

}
