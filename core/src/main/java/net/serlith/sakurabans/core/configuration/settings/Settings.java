package net.serlith.sakurabans.core.configuration.settings;

import java.net.InetAddress;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.serlith.sakurabans.api.model.AddressRangeVictim;
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
        Audit audit,
        Placeholders placeholders,
        GeoIp geoip,
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
            CommandPatterns muteCommandBlacklist, boolean enforceServerSwitch,
            Duration loginCheckTimeout, List<String> kickIgnoredReasons,
            List<String> serversWithoutLoginCheck, boolean loginCheck, boolean networkKick,
            @Nullable Duration ipBanDecay) {

        public Enforcement {
            kickIgnoredReasons = List.copyOf(kickIgnoredReasons);
            serversWithoutLoginCheck = List.copyOf(serversWithoutLoginCheck);
        }

        public boolean withoutLoginCheck(final @Nullable String server) {
            return server != null && this.serversWithoutLoginCheck.stream()
                    .anyMatch(server::equalsIgnoreCase);
        }

        /** Whether {@code /kick} leaves no record for this reason (a substring, any case). */
        public boolean kickReasonIgnored(final String reason) {
            final String lower = reason.toLowerCase(Locale.ROOT);
            return this.kickIgnoredReasons.stream().anyMatch(lower::contains);
        }
    }

    public record Alts(boolean registerOnConnection, Set<String> serversWithoutIpRegistration,
            @Nullable Duration recordExpiration, JoinNotification joinNotification,
            HiddenPlayers hiddenPlayers, AltAutoBan autoBan, int autoBanConfirmAbove) {
    }

    public record HiddenPlayers(Set<UUID> uuids, Set<String> names,
            List<AddressRangeVictim> addresses) {

        public static final HiddenPlayers NONE = new HiddenPlayers(Set.of(), Set.of(), List.of());

        public HiddenPlayers {
            uuids = Set.copyOf(uuids);
            names = Set.copyOf(names);
            addresses = List.copyOf(addresses);
        }

        public boolean empty() {
            return this.uuids.isEmpty() && this.names.isEmpty() && this.addresses.isEmpty();
        }

        public boolean hidesPlayer(final UUID uuid, final String name) {
            return this.uuids.contains(uuid) || this.names.contains(name.toLowerCase(Locale.ROOT));
        }

        public boolean hidesAddress(final InetAddress address) {
            return this.addresses.stream().anyMatch(range -> range.contains(address));
        }
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
            boolean protectsEdits, List<ExemptPlayer> players, Set<String> groups) {

        public Exemption {
            players = List.copyOf(players);
            groups = Set.copyOf(groups);
        }

        /** Whether a listed entry exempts this player from this type; {@code name} if known. */
        public boolean listed(final UUID uuid, final @Nullable String name,
                final PunishmentType type) {
            return this.players.stream().anyMatch(entry -> entry.matches(uuid, name, type));
        }
    }

    public record ExemptPlayer(@Nullable UUID uuid, @Nullable String name,
            @Nullable PunishmentType type) {

        public boolean matches(final UUID player, final @Nullable String playerName,
                final PunishmentType punishment) {
            if (this.type != null && this.type != punishment) {
                return false;
            }
            return this.uuid != null ? this.uuid.equals(player)
                    : playerName != null && playerName.equalsIgnoreCase(this.name);
        }
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

    public record Audit(@Nullable Duration retention) {
    }

    public record Placeholders(Duration refresh) {
    }

    public record GeoIp(boolean enabled, @Nullable String accountId, @Nullable String licenseKey,
            @Nullable Duration refresh, Set<String> blacklist, Set<String> whitelist,
            boolean allowUnknown) {

        public GeoIp {
            blacklist = Set.copyOf(blacklist);
            whitelist = Set.copyOf(whitelist);
        }

        /** Whether the database can be downloaded: an account ID and a licence key are set. */
        public boolean downloads() {
            return this.accountId != null && this.licenseKey != null;
        }

        @Override
        public String toString() {
            return "GeoIp[enabled=" + this.enabled + ", account="
                    + (this.downloads() ? "set" : "none") + ", refresh=" + this.refresh
                    + ", blacklist=" + this.blacklist + ", whitelist=" + this.whitelist
                    + ", allowUnknown=" + this.allowUnknown + "]";
        }
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

        private static final Map<String, List<String>> DEFAULT_LABELS = Map.of(
                "alts", List.of("alts", "dupeip"),
                "warns", List.of("warns", "warnings"));

        public List<String> labels(final String command) {
            return this.labels.getOrDefault(command,
                    DEFAULT_LABELS.getOrDefault(command, List.of(command)));
        }
    }

}
