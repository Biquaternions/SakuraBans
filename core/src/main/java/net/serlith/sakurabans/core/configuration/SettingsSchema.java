package net.serlith.sakurabans.core.configuration;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import net.serlith.sakurabans.core.model.SettingType;
import org.jspecify.annotations.Nullable;

public final class SettingsSchema {

    /** Where a key may be overridden (protocol 5.2). */
    public enum Level {
        /** Only in this server's file. */
        FILE,
        /** In the global scope only: it must be the same everywhere. */
        GLOBAL,
        /** In any scope. */
        SCOPED
    }

    /**
     * A key of {@code config.yml} ({@code reasons.default}), or of another file with the file
     * as a prefix ({@code limits:groups}, {@code webhooks:routes.ban.url}). A {@code secret}
     * value is never shown, logged or uploaded.
     */
    public record Entry(String key, SettingType type, Level level, String description,
            List<String> options, @Nullable Double min, @Nullable Double max, boolean secret) {

        public Entry {
            options = List.copyOf(options);
        }

        public boolean editable() {
            return this.level != Level.FILE;
        }

        /** {@code config}, {@code limits} or {@code webhooks}: the file the key comes from. */
        public String group() {
            return SettingsSchema.group(this.key);
        }
    }

    public static final String LIMITS = "limits:";
    public static final String WEBHOOKS = "webhooks:";

    /** The group of a key: its file prefix, or {@code config}. */
    public static String group(final String key) {
        return key.startsWith(LIMITS) ? "limits" : key.startsWith(WEBHOOKS) ? "webhooks"
                : "config";
    }

    /** The file a key lives in. */
    public static String file(final String key) {
        return key.startsWith(LIMITS) ? ConfigFiles.LIMITS
                : key.startsWith(WEBHOOKS) ? ConfigFiles.WEBHOOKS : ConfigFiles.CONFIG;
    }

    /** The key of a path inside a file: the reverse of {@link #file} and {@link #path}. */
    public static String key(final String file, final String path) {
        return switch (file) {
            case ConfigFiles.LIMITS -> LIMITS + path;
            case ConfigFiles.WEBHOOKS -> WEBHOOKS + path;
            default -> path;
        };
    }

    /** The key's path inside its file, without the prefix. */
    public static String path(final String key) {
        return key.startsWith(LIMITS) ? key.substring(LIMITS.length())
                : key.startsWith(WEBHOOKS) ? key.substring(WEBHOOKS.length()) : key;
    }

    private static final List<Entry> BASE = List.of(
            entry("server.name", SettingType.STRING, Level.FILE,
                    "This server's name for scopes (empty for none)", List.of(), null, null),
            entry("server.categories", SettingType.STRING_LIST, Level.FILE,
                    "Categories this server belongs to", List.of(), null, null),
            entry("scopes.default", SettingType.STRING, Level.FILE,
                    "Where punishments apply when no scope is given", List.of(), null, null),
            entry("scopes.require-permissions", SettingType.BOOLEAN, Level.GLOBAL,
                    "Whether staff need scope permissions", List.of(), null, null),
            entry("scopes.categories", SettingType.MAP, Level.GLOBAL,
                    "Categories and their servers", List.of(), null, null),
            entry("web.url", SettingType.STRING, Level.FILE,
                    "The web editor's address (empty: off)", List.of(), null, null),
            entry("web.token", SettingType.STRING, Level.FILE,
                    "The web editor's upload token (secret)", List.of(), null, null),
            entry("web.live-apply", SettingType.BOOLEAN, Level.FILE,
                    "Whether saves in an open editor apply without a command", List.of(), null,
                    null),
            entry("enforcement.address-strictness", SettingType.ENUM, Level.GLOBAL,
                    "How far address bans reach",
                    List.of("LENIENT", "NORMAL", "STERN", "STRICT"), null, null),
            entry("enforcement.composite-by-default", SettingType.BOOLEAN, Level.SCOPED,
                    "Whether bans also ban the current address", List.of(), null, null),
            entry("enforcement.mute-command-blacklist", SettingType.STRING_LIST, Level.SCOPED,
                    "Commands muted players can't use", List.of(), null, null),
            entry("enforcement.kick-ignored-reasons", SettingType.STRING_LIST, Level.SCOPED,
                    "Kick reasons that leave no record (a substring, any case)", List.of(), null,
                    null),
            entry("enforcement.enforce-server-switch", SettingType.BOOLEAN, Level.FILE,
                    "Whether a proxy checks server scopes on switches", List.of(), null, null),
            entry("enforcement.login-check-timeout", SettingType.DURATION, Level.FILE,
                    "How long a join may wait for the ban check", List.of(), null, null),
            entry("enforcement.servers-without-login-check", SettingType.STRING_LIST,
                    Level.GLOBAL, "Proxy: servers banned players may still join, tried in order"
                            + " when a ban is enforced", List.of(), null, null),
            entry("enforcement.login-check", SettingType.BOOLEAN, Level.FILE,
                    "Backend: whether this server turns banned players away", List.of(), null,
                    null),
            entry("enforcement.ip-ban-decay", SettingType.STRING, Level.GLOBAL,
                    "After how long a ban of player and address reaches the player only (a"
                            + " duration or never)", List.of(), null, null),
            entry("enforcement.network-kick", SettingType.BOOLEAN, Level.SCOPED,
                    "Backend behind a proxy: whether a ban disconnects from the whole network",
                    List.of(), null, null),
            entry("alts.register-on-connection", SettingType.BOOLEAN, Level.GLOBAL,
                    "Whether names and addresses are recorded", List.of(), null, null),
            entry("alts.servers-without-ip-registration", SettingType.STRING_LIST, Level.GLOBAL,
                    "Servers that never record addresses", List.of(), null, null),
            entry("alts.record-expiration", SettingType.STRING, Level.GLOBAL,
                    "How long address records count (a duration or never)", List.of(), null, null),
            entry("alts.join-notification.enabled", SettingType.BOOLEAN, Level.SCOPED,
                    "Whether staff hear about joining players' alts", List.of(), null, null),
            entry("alts.join-notification.only-if-punished", SettingType.BOOLEAN, Level.SCOPED,
                    "Only when an alt is punished", List.of(), null, null),
            entry("alts.join-notification.threshold", SettingType.INTEGER, Level.SCOPED,
                    "The fewest alts that trigger a notice", List.of(), 1.0, null),
            entry("alts.join-notification.limit", SettingType.INTEGER, Level.SCOPED,
                    "The most alts listed in a notice", List.of(), 1.0, null),
            entry("alts.auto-ban", SettingType.ENUM, Level.GLOBAL,
                    "Whether bans also ban linked accounts (needs /sb accept alts once)",
                    List.of("DISABLED", "ON_BAN", "ON_JOIN"), null, null),
            entry("alts.auto-ban-confirm-above", SettingType.INTEGER, Level.GLOBAL,
                    "More linked accounts than this need --confirm on /ban", List.of(), 0.0,
                    null),
            entry("alts.hidden-players", SettingType.STRING_LIST, Level.GLOBAL,
                    "Names, UUIDs or addresses alt listings leave out (unless viewhidden)",
                    List.of(), null, null),
            entry("reasons.default", SettingType.STRING, Level.SCOPED,
                    "The reason used when none is given", List.of(), null, null),
            entry("reasons.require.ban", SettingType.BOOLEAN, Level.SCOPED,
                    "Whether bans need a reason", List.of(), null, null),
            entry("reasons.require.mute", SettingType.BOOLEAN, Level.SCOPED,
                    "Whether mutes need a reason", List.of(), null, null),
            entry("reasons.require.warn", SettingType.BOOLEAN, Level.SCOPED,
                    "Whether warnings need a reason", List.of(), null, null),
            entry("reasons.require.kick", SettingType.BOOLEAN, Level.SCOPED,
                    "Whether kicks need a reason", List.of(), null, null),
            entry("reasons.require.revoke", SettingType.BOOLEAN, Level.SCOPED,
                    "Whether revocations need a reason", List.of(), null, null),
            entry("reasons.allow-newlines", SettingType.BOOLEAN, Level.SCOPED,
                    "Whether reasons may contain line breaks", List.of(), null, null),
            entry("reasons.shortcuts", SettingType.MAP, Level.SCOPED,
                    "Reason shortcuts (name to text)", List.of(), null, null),
            entry("exemption.provider", SettingType.ENUM, Level.GLOBAL,
                    "Who can't be punished",
                    List.of("NONE", "PERMISSION", "LUCKPERMS_WEIGHT"), null, null),
            entry("exemption.permit-same-weight", SettingType.BOOLEAN, Level.GLOBAL,
                    "Whether staff may punish the same weight", List.of(), null, null),
            entry("exemption.protects-edits", SettingType.BOOLEAN, Level.GLOBAL,
                    "Whether exemption protects punishments from changes", List.of(), null, null),
            entry("exemption.players", SettingType.STRING_LIST, Level.GLOBAL,
                    "Players who can't be punished: name or UUID, optionally :type",
                    List.of(), null, null),
            entry("exemption.groups", SettingType.STRING_LIST, Level.GLOBAL,
                    "LuckPerms groups whose members can't be punished", List.of(), null, null),
            entry("warnings.expire-after", SettingType.STRING, Level.SCOPED,
                    "How long warnings stay active (a duration or never)", List.of(), null, null),
            entry("warnings.actions", SettingType.MAP, Level.SCOPED,
                    "Console commands per number of active warnings", List.of(), null, null),
            entry("display.default-locale", SettingType.STRING, Level.SCOPED,
                    "The language when the player's isn't known", List.of(), null, null),
            entry("display.date-format", SettingType.STRING, Level.SCOPED,
                    "How dates are shown", List.of(), null, null),
            entry("display.timezone", SettingType.STRING, Level.SCOPED,
                    "The time zone for dates", List.of(), null, null),
            entry("display.id-style", SettingType.ENUM, Level.GLOBAL,
                    "How punishment ids are shown",
                    List.of("NUMERIC", "SCRAMBLED", "BOTH"), null, null),
            entry("display.censor-ip-addresses", SettingType.BOOLEAN, Level.SCOPED,
                    "Whether addresses are censored without viewips", List.of(), null, null),
            entry("lockdown.persist", SettingType.BOOLEAN, Level.GLOBAL,
                    "Whether lockdowns survive a restart", List.of(), null, null),
            entry("notifications.attempt-throttle", SettingType.DURATION, Level.SCOPED,
                    "The shortest time between attempt notices", List.of(), null, null),
            entry("players.remote-lookup", SettingType.BOOLEAN, Level.FILE,
                    "Whether unknown names are looked up remotely", List.of(), null, null),
            entry("audit.retention", SettingType.STRING, Level.GLOBAL,
                    "How long audit entries are kept (a duration or never)", List.of(), null,
                    null),
            entry("placeholders.refresh", SettingType.DURATION, Level.GLOBAL,
                    "How often the network's punishment counts are read (at least 30s)",
                    List.of(), null, null),
            entry("geoip.enabled", SettingType.BOOLEAN, Level.FILE,
                    "Whether this server looks up countries (needs a restart)", List.of(), null,
                    null),
            entry("geoip.account-id", SettingType.STRING, Level.FILE,
                    "The MaxMind account ID for downloads (secret)", List.of(), null, null),
            entry("geoip.license-key", SettingType.STRING, Level.FILE,
                    "The MaxMind licence key for downloads (secret)", List.of(), null, null),
            entry("geoip.refresh", SettingType.STRING, Level.FILE,
                    "How often the database is checked for a newer copy (at least 1d, or never)",
                    List.of(), null, null),
            entry("geoip.blacklist", SettingType.STRING_LIST, Level.GLOBAL,
                    "Countries (ISO codes like US) whose players can't join", List.of(), null,
                    null),
            entry("geoip.whitelist", SettingType.STRING_LIST, Level.GLOBAL,
                    "When not empty, the only countries (ISO codes) whose players can join",
                    List.of(), null, null),
            entry("geoip.allow-unknown", SettingType.BOOLEAN, Level.GLOBAL,
                    "Whether players whose country isn't known may join while a list is set",
                    List.of(), null, null),
            entry("commands.labels", SettingType.MAP, Level.FILE,
                    "Command names and aliases", List.of(), null, null),
            entry("limits:reduce-to-limit", SettingType.BOOLEAN, Level.SCOPED,
                    "Whether a punishment over the limit is shortened instead of refused",
                    List.of(), null, null),
            entry("limits:console-cooldown", SettingType.DURATION, Level.SCOPED,
                    "How long the console may not punish the same player again (0 for none)",
                    List.of(), null, null),
            entry("limits:groups", SettingType.MAP, Level.SCOPED,
                    "Staff groups and their limits", List.of(), null, null),
            entry("webhooks:enabled", SettingType.BOOLEAN, Level.SCOPED,
                    "Whether webhooks are sent", List.of(), null, null),
            secret("webhooks:url", "The webhook URL routes use unless they name their own"),
            entry("webhooks:username", SettingType.STRING, Level.SCOPED,
                    "The name the webhook posts as", List.of(), null, null),
            entry("webhooks:avatar-url", SettingType.STRING, Level.SCOPED,
                    "The avatar the webhook posts with", List.of(), null, null),
            entry("webhooks:include-silent", SettingType.BOOLEAN, Level.SCOPED,
                    "Whether silent punishments are posted too", List.of(), null, null));

    /** The webhook routes and what their keys call them. */
    private static final Map<String, String> ROUTES = routes();

    public static final List<Entry> ENTRIES = withRoutes();

    /**
     * Never shown, logged or uploaded: the secret entries, the editor's upload token and the
     * MaxMind account.
     */
    public static final Set<String> SECRET = secrets();

    private static final Map<String, Entry> BY_KEY = ENTRIES.stream()
            .collect(Collectors.toUnmodifiableMap(Entry::key, Function.identity()));

    private SettingsSchema() {
    }

    public static Optional<Entry> entry(final String key) {
        return Optional.ofNullable(BY_KEY.get(key));
    }

    private static Entry entry(final String key, final SettingType type, final Level level,
            final String description, final List<String> options, final @Nullable Double min,
            final @Nullable Double max) {
        return new Entry(key, type, level, description, options, min, max, false);
    }

    private static Set<String> secrets() {
        final Set<String> secrets = new HashSet<>(Set.of("web.token", "geoip.account-id",
                "geoip.license-key"));
        ENTRIES.stream().filter(Entry::secret).forEach(entry -> secrets.add(entry.key()));
        return Set.copyOf(secrets);
    }

    public static boolean secret(final String key) {
        return SECRET.contains(key);
    }

    private static Entry secret(final String key, final String description) {
        return new Entry(key, SettingType.STRING, Level.SCOPED, description, List.of(), null,
                null, true);
    }

    private static Map<String, String> routes() {
        final Map<String, String> routes = new LinkedHashMap<>();
        routes.put("ban", "bans");
        routes.put("mute", "mutes");
        routes.put("warn", "warnings");
        routes.put("kick", "kicks");
        routes.put("unban", "unbans");
        routes.put("unmute", "unmutes");
        routes.put("unwarn", "removed warnings");
        routes.put("void", "voided punishments");
        routes.put("delete", "deleted punishments");
        routes.put("edit", "changed punishments");
        routes.put("rollback", "staff rollbacks");
        return routes;
    }

    // One flat key per route field (protocol 5.2): webhooks:routes.<event>.<field>.
    private static List<Entry> withRoutes() {
        final List<Entry> all = new ArrayList<>(BASE);
        ROUTES.forEach((event, what) -> {
            final String prefix = WEBHOOKS + "routes." + event + ".";
            all.add(entry(prefix + "enabled", SettingType.BOOLEAN, Level.SCOPED,
                    "Whether " + what + " are posted", List.of(), null, null));
            all.add(secret(prefix + "url", "The webhook URL for " + what
                    + " (empty: webhooks:url)"));
            all.add(entry(prefix + "title", SettingType.STRING, Level.SCOPED,
                    "The embed title for " + what, List.of(), null, null));
            all.add(entry(prefix + "color", SettingType.STRING, Level.SCOPED,
                    "The embed color for " + what + " (#rrggbb)", List.of(), null, null));
            all.add(entry(prefix + "description", SettingType.STRING, Level.SCOPED,
                    "The embed text for " + what, List.of(), null, null));
            all.add(entry(prefix + "fields", SettingType.MAP, Level.SCOPED,
                    "The embed fields for " + what + ", in order", List.of(), null, null));
        });
        return List.copyOf(all);
    }

}
