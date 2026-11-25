package net.serlith.sakurabans.core.configuration;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import net.serlith.sakurabans.core.model.SettingType;
import org.jspecify.annotations.Nullable;

public final class SettingsSchema {

    public enum Scope {
        NETWORK,
        INSTANCE,
        RESTART
    }

    public record Entry(String key, SettingType type, Scope scope, String description,
            List<String> options, @Nullable Double min, @Nullable Double max) {

        public Entry {
            options = List.copyOf(options);
        }

        public boolean editable() {
            return this.scope == Scope.NETWORK;
        }
    }

    public static final List<Entry> ENTRIES = List.of(
            entry("server.name", SettingType.STRING, Scope.INSTANCE,
                    "This server's name for scopes, or auto", List.of(), null, null),
            entry("server.categories", SettingType.STRING_LIST, Scope.INSTANCE,
                    "Categories this server belongs to", List.of(), null, null),
            entry("scopes.default", SettingType.STRING, Scope.INSTANCE,
                    "Where punishments apply when no scope is given", List.of(), null, null),
            entry("scopes.require-permissions", SettingType.BOOLEAN, Scope.NETWORK,
                    "Whether staff need scope permissions", List.of(), null, null),
            entry("scopes.categories", SettingType.MAP, Scope.NETWORK,
                    "Categories and their servers", List.of(), null, null),
            entry("enforcement.address-strictness", SettingType.ENUM, Scope.NETWORK,
                    "How far address bans reach",
                    List.of("LENIENT", "NORMAL", "STERN", "STRICT"), null, null),
            entry("enforcement.composite-by-default", SettingType.BOOLEAN, Scope.NETWORK,
                    "Whether bans also ban the current address", List.of(), null, null),
            entry("enforcement.mute-command-blacklist", SettingType.STRING_LIST, Scope.INSTANCE,
                    "Commands muted players can't use", List.of(), null, null),
            entry("enforcement.deny-banned-joins", SettingType.BOOLEAN, Scope.INSTANCE,
                    "Whether this instance turns banned players away", List.of(), null, null),
            entry("enforcement.enforce-server-switch", SettingType.BOOLEAN, Scope.INSTANCE,
                    "Whether a proxy checks server scopes on switches", List.of(), null, null),
            entry("enforcement.login-check-timeout", SettingType.DURATION, Scope.INSTANCE,
                    "How long a join may wait for the ban check", List.of(), null, null),
            entry("alts.register-on-connection", SettingType.BOOLEAN, Scope.NETWORK,
                    "Whether names and addresses are recorded", List.of(), null, null),
            entry("alts.servers-without-ip-registration", SettingType.STRING_LIST, Scope.NETWORK,
                    "Servers that never record addresses", List.of(), null, null),
            entry("alts.record-expiration", SettingType.STRING, Scope.NETWORK,
                    "How long address records count (a duration or never)", List.of(), null, null),
            entry("alts.join-notification.enabled", SettingType.BOOLEAN, Scope.NETWORK,
                    "Whether staff hear about joining players' alts", List.of(), null, null),
            entry("alts.join-notification.only-if-punished", SettingType.BOOLEAN, Scope.NETWORK,
                    "Only when an alt is punished", List.of(), null, null),
            entry("alts.join-notification.threshold", SettingType.INTEGER, Scope.NETWORK,
                    "The fewest alts that trigger a notice", List.of(), 1.0, null),
            entry("alts.join-notification.limit", SettingType.INTEGER, Scope.NETWORK,
                    "The most alts listed in a notice", List.of(), 1.0, null),
            entry("reasons.default", SettingType.STRING, Scope.NETWORK,
                    "The reason used when none is given", List.of(), null, null),
            entry("reasons.require.ban", SettingType.BOOLEAN, Scope.NETWORK,
                    "Whether bans need a reason", List.of(), null, null),
            entry("reasons.require.mute", SettingType.BOOLEAN, Scope.NETWORK,
                    "Whether mutes need a reason", List.of(), null, null),
            entry("reasons.require.warn", SettingType.BOOLEAN, Scope.NETWORK,
                    "Whether warnings need a reason", List.of(), null, null),
            entry("reasons.require.kick", SettingType.BOOLEAN, Scope.NETWORK,
                    "Whether kicks need a reason", List.of(), null, null),
            entry("reasons.require.revoke", SettingType.BOOLEAN, Scope.NETWORK,
                    "Whether revocations need a reason", List.of(), null, null),
            entry("reasons.allow-newlines", SettingType.BOOLEAN, Scope.NETWORK,
                    "Whether reasons may contain line breaks", List.of(), null, null),
            entry("reasons.shortcuts", SettingType.MAP, Scope.NETWORK,
                    "Reason shortcuts (name to text)", List.of(), null, null),
            entry("exemption.provider", SettingType.ENUM, Scope.NETWORK,
                    "Who can't be punished",
                    List.of("NONE", "PERMISSION", "LUCKPERMS_WEIGHT"), null, null),
            entry("exemption.permit-same-weight", SettingType.BOOLEAN, Scope.NETWORK,
                    "Whether staff may punish the same weight", List.of(), null, null),
            entry("exemption.protects-edits", SettingType.BOOLEAN, Scope.NETWORK,
                    "Whether exemption protects punishments from changes", List.of(), null, null),
            entry("warnings.expire-after", SettingType.STRING, Scope.NETWORK,
                    "How long warnings stay active (a duration or never)", List.of(), null, null),
            entry("warnings.actions", SettingType.MAP, Scope.NETWORK,
                    "Console commands per number of active warnings", List.of(), null, null),
            entry("display.default-locale", SettingType.STRING, Scope.NETWORK,
                    "The language when the player's isn't known", List.of(), null, null),
            entry("display.date-format", SettingType.STRING, Scope.NETWORK,
                    "How dates are shown", List.of(), null, null),
            entry("display.timezone", SettingType.STRING, Scope.NETWORK,
                    "The time zone for dates", List.of(), null, null),
            entry("display.id-style", SettingType.ENUM, Scope.NETWORK,
                    "How punishment ids are shown",
                    List.of("NUMERIC", "SCRAMBLED", "BOTH"), null, null),
            entry("display.censor-ip-addresses", SettingType.BOOLEAN, Scope.NETWORK,
                    "Whether addresses are censored without viewips", List.of(), null, null),
            entry("lockdown.persist", SettingType.BOOLEAN, Scope.NETWORK,
                    "Whether lockdowns survive a restart", List.of(), null, null),
            entry("notifications.attempt-throttle", SettingType.DURATION, Scope.NETWORK,
                    "The shortest time between attempt notices", List.of(), null, null),
            entry("notifications.send-to-players", SettingType.BOOLEAN, Scope.INSTANCE,
                    "Whether this instance tells its players", List.of(), null, null),
            entry("players.remote-lookup", SettingType.BOOLEAN, Scope.INSTANCE,
                    "Whether unknown names are looked up remotely", List.of(), null, null),
            entry("commands.labels", SettingType.MAP, Scope.RESTART,
                    "Command names and aliases", List.of(), null, null),
            entry("commands.kinds", SettingType.MAP, Scope.RESTART,
                    "Commands for punishment kinds", List.of(), null, null));

    private static final Map<String, Entry> BY_KEY = ENTRIES.stream()
            .collect(Collectors.toUnmodifiableMap(Entry::key, Function.identity()));

    private SettingsSchema() {
    }

    public static Optional<Entry> entry(final String key) {
        return Optional.ofNullable(BY_KEY.get(key));
    }

    private static Entry entry(final String key, final SettingType type, final Scope scope,
            final String description, final List<String> options, final @Nullable Double min,
            final @Nullable Double max) {
        return new Entry(key, type, scope, description, options, min, max);
    }

}
