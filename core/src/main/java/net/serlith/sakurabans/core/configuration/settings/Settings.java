package net.serlith.sakurabans.core.configuration.settings;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.serlith.sakurabans.api.model.AddressStrictness;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.model.Scope;
import org.jspecify.annotations.Nullable;

public record Settings(
        Server server,
        Scopes scopes,
        Enforcement enforcement,
        Alts alts,
        Reasons reasons,
        Exemption exemption,
        Warnings warnings,
        Display display,
        Lockdown lockdown,
        Notifications notifications,
        Players players) {

    public record Server(@Nullable String name, List<String> categories, ServerRole role) {
    }

    public record Scopes(DefaultScope defaultScope, boolean requirePermissions) {
    }

    public sealed interface DefaultScope {

        record Local() implements DefaultScope {
        }

        record Fixed(Scope scope) implements DefaultScope {
        }
    }

    public record Enforcement(AddressStrictness addressStrictness, boolean compositeByDefault,
            Set<String> muteCommandBlacklist, boolean enforceServerSwitch,
            Duration loginCheckTimeout) {
    }

    public record Alts(boolean registerOnConnection, Set<String> serversWithoutIpRegistration,
            @Nullable Duration recordExpiration, JoinNotification joinNotification,
            ConnectionLimiter connectionLimiter) {
    }

    public record JoinNotification(boolean enabled, boolean onlyIfPunished, int threshold,
            int limit) {
    }

    public record ConnectionLimiter(boolean enabled, int limit, Duration window) {
    }

    public record Reasons(String defaultReason, Set<PunishmentType> requiredFor,
            boolean requiredForRevoke, boolean allowNewlines) {
    }

    public record Exemption(ExemptionProvider provider, boolean permitSameWeight,
            boolean protectsEdits) {
    }

    public record Warnings(@Nullable Duration expireAfter) {
    }

    public record Display(Locale defaultLocale, String dateFormat, ZoneId zone, IdStyle idStyle,
            boolean censorIpAddresses) {

        public String formatInstant(final Instant instant, final Locale locale) {
            return DateTimeFormatter.ofPattern(this.dateFormat, locale).withZone(this.zone)
                    .format(instant);
        }
    }

    public record Lockdown(boolean persist) {
    }

    /** {@code attemptThrottle}: at most one "tried to join/chat" notice per player this often. */
    public record Notifications(Duration attemptThrottle) {
    }

    /** {@code remoteLookup}: whether unseen names are looked up through the platform. */
    public record Players(boolean remoteLookup) {
    }
}
