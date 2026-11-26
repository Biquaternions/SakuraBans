package net.serlith.sakurabans.core.configuration.settings;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
        Players players,
        Web web,
        Commands commands) {

    public record Server(@Nullable String name, List<String> categories) {
    }

    public record Scopes(DefaultScope defaultScope, boolean requirePermissions,
            Map<String, Set<String>> categories) {
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
            @Nullable Duration recordExpiration, JoinNotification joinNotification) {
    }

    public record JoinNotification(boolean enabled, boolean onlyIfPunished, int threshold,
            int limit) {
    }

    public record Reasons(String defaultReason, Set<PunishmentType> requiredFor,
            boolean requiredForRevoke, boolean allowNewlines, Map<String, String> shortcuts) {

        public Reasons {
            shortcuts = Map.copyOf(shortcuts);
        }
    }

    public record Exemption(ExemptionProvider provider, boolean permitSameWeight,
            boolean protectsEdits) {
    }

    public record Warnings(@Nullable Duration expireAfter, Map<Integer, List<String>> actions) {

        public Warnings {
            actions = Map.copyOf(actions);
        }
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

    public record Notifications(Duration attemptThrottle) {
    }

    public record Players(boolean remoteLookup) {
    }

    /**
     * {@code url} without a trailing slash, or null while the editor is off. {@code token} is
     * secret: never log or show it.
     */
    public record Web(@Nullable URI url, @Nullable String token, boolean liveApply) {

        @Override
        public String toString() {
            return "Web[url=" + this.url + ", token=" + (this.token == null ? "none" : "set")
                    + ", liveApply=" + this.liveApply + "]";
        }
    }

    public record Commands(Map<String, List<String>> labels) {

        public Commands {
            labels = Map.copyOf(labels);
        }

        public List<String> labels(final String command) {
            return this.labels.getOrDefault(command, List.of(command));
        }
    }

}
