package net.serlith.sakurabans.core.punish;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import net.serlith.sakurabans.api.model.AddressRangeVictim;
import net.serlith.sakurabans.api.model.AddressVictim;
import net.serlith.sakurabans.api.model.CompositeVictim;
import net.serlith.sakurabans.api.model.ConsoleOperator;
import net.serlith.sakurabans.api.model.CustomOperator;
import net.serlith.sakurabans.api.model.ErasedAddressVictim;
import net.serlith.sakurabans.api.model.PlayerOperator;
import net.serlith.sakurabans.api.model.PlayerVictim;
import net.serlith.sakurabans.api.model.Punishment;
import net.serlith.sakurabans.core.platform.Platform;
import net.serlith.sakurabans.core.util.Durations;

public final class ConsoleActions {

    private ConsoleActions() {
    }

    public static Map<String, String> values(final Punishment punishment,
            final Function<UUID, Optional<String>> names) {
        final String victim = switch (punishment.victim()) {
            case PlayerVictim player -> names.apply(player.uuid())
                    .orElse(player.uuid().toString());
            case CompositeVictim composite -> names.apply(composite.uuid())
                    .orElse(composite.uuid().toString());
            case AddressVictim address -> address.address().getHostAddress();
            case AddressRangeVictim range -> range.network().getHostAddress() + "/"
                    + range.prefixLength();
            // Never on a new punishment; kept for completeness.
            case ErasedAddressVictim erased -> erased.uuid()
                    .map(uuid -> names.apply(uuid).orElse(uuid.toString()))
                    .orElse("");
        };
        final String uuid = switch (punishment.victim()) {
            case PlayerVictim player -> player.uuid().toString();
            case CompositeVictim composite -> composite.uuid().toString();
            case ErasedAddressVictim erased -> erased.uuid().map(UUID::toString).orElse("");
            default -> "";
        };
        final String address = switch (punishment.victim()) {
            case AddressVictim single -> single.address().getHostAddress();
            case CompositeVictim composite -> composite.address().getHostAddress();
            default -> "";
        };
        final String operator = switch (punishment.operator()) {
            case ConsoleOperator console -> "Console";
            case PlayerOperator player -> player.name();
            case CustomOperator custom -> custom.name();
        };
        final Optional<Duration> duration = punishment.expiresAt()
                .map(expiry -> Duration.between(punishment.createdAt(), expiry));
        final Map<String, String> values = new HashMap<>();
        values.put("victim", victim);
        values.put("uuid", uuid);
        values.put("address", address);
        values.put("id", Long.toString(punishment.id()));
        values.put("operator", operator);
        values.put("reason", punishment.reason());
        values.put("duration", duration.map(value -> Durations.formatCompact(value)
                .replace(" ", "")).orElse("permanent"));
        return values;
    }

    public static String fill(final String command, final Map<String, String> values) {
        String filled = command;
        for (final Map.Entry<String, String> value : values.entrySet()) {
            filled = filled.replace("{" + value.getKey() + "}",
                    value.getValue().replaceAll("\\R", " "));
        }
        return filled;
    }

    public static void run(final Platform platform, final List<String> commands,
            final Map<String, String> values, final ComponentLogger logger, final String what) {
        for (final String command : commands) {
            final String filled = fill(command, values);
            try {
                platform.dispatchConsoleCommand(filled);
            } catch (final RuntimeException e) {
                logger.warn("The action '{}' of {} failed", filled, what, e);
            }
        }
    }

}
