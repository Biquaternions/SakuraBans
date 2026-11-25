package net.serlith.sakurabans.core.configuration;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.serlith.sakurabans.api.model.CategoryScope;
import net.serlith.sakurabans.api.model.GlobalScope;
import net.serlith.sakurabans.api.model.Lengths;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.model.ServerScope;
import net.serlith.sakurabans.api.validation.Validator;
import net.serlith.sakurabans.core.configuration.file.AltsSection;
import net.serlith.sakurabans.core.configuration.file.CommandsSection;
import net.serlith.sakurabans.core.configuration.file.DisplaySection;
import net.serlith.sakurabans.core.configuration.file.LimitsConfig;
import net.serlith.sakurabans.core.configuration.file.MainConfig;
import net.serlith.sakurabans.core.configuration.file.ReasonsSection;
import net.serlith.sakurabans.core.configuration.file.StorageConfig;
import net.serlith.sakurabans.core.configuration.file.WarningsSection;
import net.serlith.sakurabans.core.configuration.file.WebhooksConfig;
import net.serlith.sakurabans.core.configuration.settings.LimitSettings;
import net.serlith.sakurabans.core.configuration.settings.Settings;
import net.serlith.sakurabans.core.configuration.settings.StorageSettings;
import net.serlith.sakurabans.core.configuration.settings.StorageType;
import net.serlith.sakurabans.core.configuration.settings.SyncMode;
import net.serlith.sakurabans.core.configuration.settings.WebhookSettings;
import net.serlith.sakurabans.core.util.Durations;
import org.jspecify.annotations.Nullable;

final class SettingsMapper {

    private static final Duration MIN_POLL = Duration.ofMillis(250);
    private static final Duration MAX_POLL = Duration.ofSeconds(30);
    private static final Pattern TABLE_PREFIX = Pattern.compile("[a-z][a-z0-9_]{0,15}");
    private static final Pattern SQL_NAME = Pattern.compile("[a-z_][a-z0-9_]{0,62}");
    private static final Duration MAX_LOGIN_TIMEOUT = Duration.ofSeconds(30);
    private static final Pattern LABEL = Pattern.compile("[a-z0-9_-]{1,32}");
    private static final Pattern COLOR = Pattern.compile("#([0-9a-fA-F]{6})");

    private final String file;
    private final List<ConfigIssue> errors = new ArrayList<>();

    private SettingsMapper(final String file) {
        this.file = file;
    }

    record Mapped<T extends @Nullable Object>(T value, List<ConfigIssue> errors) {
    }

    static Mapped<@Nullable Settings> settings(final MainConfig config) {
        final SettingsMapper mapper = new SettingsMapper(ConfigFiles.CONFIG);
        final Settings settings = mapper.mapSettings(config);
        return new Mapped<>(mapper.errors.isEmpty() ? settings : null, List.copyOf(mapper.errors));
    }

    static Mapped<@Nullable LimitSettings> limits(final LimitsConfig config) {
        final SettingsMapper mapper = new SettingsMapper(ConfigFiles.LIMITS);
        final LimitSettings limits = mapper.mapLimits(config);
        return new Mapped<>(mapper.errors.isEmpty() ? limits : null, List.copyOf(mapper.errors));
    }

    private LimitSettings mapLimits(final LimitsConfig config) {
        final List<LimitSettings.Group> groups = new ArrayList<>();
        config.groups().forEach((name, group) -> {
            final String path = "groups." + name;
            this.check(!group.permission().isBlank(), path + ".permission",
                    "must name the permission that puts staff in this group");
            final Map<PunishmentType, Duration> max = new EnumMap<>(PunishmentType.class);
            group.maxDuration().forEach((type, value) -> {
                final String key = path + ".max-duration." + type;
                this.type(key, type).ifPresent(parsed -> {
                    this.check(!parsed.instant(), key, "a kick has no duration");
                    if (!Durations.isPermanentToken(value)) {
                        this.duration(key, value).ifPresent(duration -> max.put(parsed,
                                duration));
                    }
                });
            });
            final Map<PunishmentType, Duration> cooldowns = new EnumMap<>(PunishmentType.class);
            group.cooldown().forEach((type, value) -> {
                final String key = path + ".cooldown." + type;
                this.type(key, type).ifPresent(parsed -> this.duration(key, value)
                        .ifPresent(duration -> cooldowns.put(parsed, duration)));
            });
            groups.add(new LimitSettings.Group(name, group.permission().strip(), max, cooldowns,
                    group.requireTemplate()));
        });
        return new LimitSettings(config.reduceToLimit(), groups);
    }

    static Mapped<@Nullable WebhookSettings> webhooks(final WebhooksConfig config) {
        final SettingsMapper mapper = new SettingsMapper(ConfigFiles.WEBHOOKS);
        final WebhookSettings webhooks = mapper.mapWebhooks(config);
        return new Mapped<>(mapper.errors.isEmpty() ? webhooks : null,
                List.copyOf(mapper.errors));
    }

    private WebhookSettings mapWebhooks(final WebhooksConfig config) {
        final URI url = this.uri("url", config.url());
        final Map<String, WebhookSettings.Route> routes = new LinkedHashMap<>();
        config.routes().forEach((name, route) -> {
            final String path = "routes." + name;
            if (!WebhookSettings.ROUTES.contains(name)) {
                this.error(path, "is not an event; use " + String.join(", ",
                        WebhookSettings.ROUTES.stream().sorted().toList()));
                return;
            }
            final URI own = this.uri(path + ".url", route.url());
            this.check(!config.enabled() || !route.enabled() || own != null || url != null,
                    path + ".url", "is empty, and so is the default url");
            int color = 0;
            final Matcher hex = COLOR.matcher(route.color().strip());
            if (hex.matches()) {
                color = Integer.parseInt(hex.group(1), 16);
            } else {
                this.error(path + ".color", "\"" + route.color()
                        + "\" is not a colour like #e74c3c");
            }
            final List<WebhookSettings.Field> fields = new ArrayList<>();
            route.fields().forEach((field, value) -> fields.add(
                    new WebhookSettings.Field(field, value)));
            routes.put(name, new WebhookSettings.Route(route.enabled(), own, route.title(),
                    color, route.description(), fields));
        });
        return new WebhookSettings(config.enabled(), url, config.username(),
                this.uri("avatar-url", config.avatarUrl()), config.includeSilent(), routes);
    }

    private @Nullable URI uri(final String path, final String text) {
        if (text.isBlank()) {
            return null;
        }
        try {
            final URI uri = new URI(text.strip());
            if (("https".equals(uri.getScheme()) || "http".equals(uri.getScheme()))
                    && uri.getHost() != null) {
                return uri;
            }
        } catch (final URISyntaxException e) {
            // Reported below.
        }
        this.error(path, "is not an http(s) URL");
        return null;
    }

    private Optional<PunishmentType> type(final String path, final String value) {
        for (final PunishmentType type : PunishmentType.values()) {
            if (type.name().equalsIgnoreCase(value.strip())) {
                return Optional.of(type);
            }
        }
        this.error(path, "\"" + value + "\" is not ban, mute, warn or kick");
        return Optional.empty();
    }

    private Optional<Duration> duration(final String path, final String value) {
        final Optional<Duration> duration = Durations.parse(value.strip());
        if (duration.isEmpty()) {
            this.error(path, "\"" + value + "\" is not a duration (like 30s, 10m, 7d)");
        }
        return duration;
    }

    static Mapped<@Nullable StorageSettings> storage(final StorageConfig config) {
        final SettingsMapper mapper = new SettingsMapper(ConfigFiles.STORAGE);
        final StorageSettings storage = mapper.mapStorage(config);
        return new Mapped<>(mapper.errors.isEmpty() ? storage : null, List.copyOf(mapper.errors));
    }

    private Settings mapSettings(final MainConfig config) {
        final String name = config.server().name();
        final Optional<String> serverName = name.equalsIgnoreCase("auto")
                ? Optional.empty()
                : this.identifier("server.name", name);
        final List<String> categories = config.server().categories().stream()
                .flatMap(category -> this.identifier("server.categories", category).stream())
                .toList();
        final Settings.Server server =
                new Settings.Server(serverName.orElse(null), categories);

        final Settings.Scopes scopes = new Settings.Scopes(
                this.defaultScope(config.scopes().defaultScope()),
                config.scopes().requirePermissions(),
                this.categories(config.scopes().categories()));

        final var enforcement = config.enforcement();
        this.check(enforcement.loginCheckTimeout().compareTo(MAX_LOGIN_TIMEOUT) <= 0,
                "enforcement.login-check-timeout", "must be at most 30s");
        final Set<String> blacklist = new LinkedHashSet<>();
        enforcement.muteCommandBlacklist().forEach(command ->
                blacklist.add(command.strip().toLowerCase(Locale.ROOT).replaceFirst("^/", "")));
        final Settings.Enforcement enforcementSettings = new Settings.Enforcement(
                enforcement.addressStrictness(), enforcement.compositeByDefault(),
                Set.copyOf(blacklist), enforcement.denyBannedJoins(),
                enforcement.enforceServerSwitch(),
                enforcement.loginCheckTimeout());

        final Settings.Alts alts = this.alts(config.alts());
        final Settings.Reasons reasons = this.reasons(config.reasons());
        final Settings.Exemption exemption = new Settings.Exemption(
                config.exemption().provider(), config.exemption().permitSameWeight(),
                config.exemption().protectsEdits());
        final Settings.Warnings warnings = this.warnings(config.warnings());
        final Settings.Display display = this.display(config.display());
        this.check(!config.notifications().attemptThrottle().isNegative(),
                "notifications.attempt-throttle", "must not be negative");
        return new Settings(server, scopes, enforcementSettings, alts, reasons, exemption,
                warnings, display, new Settings.Lockdown(config.lockdown().persist()),
                new Settings.Notifications(config.notifications().attemptThrottle(),
                        config.notifications().sendToPlayers()),
                new Settings.Players(config.players().remoteLookup()),
                this.commands(config.commands()));
    }

    private Settings.Alts alts(final AltsSection alts) {
        final Set<String> servers = new LinkedHashSet<>();
        alts.serversWithoutIpRegistration().forEach(server ->
                this.identifier("alts.servers-without-ip-registration", server)
                        .ifPresent(servers::add));
        final var join = alts.joinNotification();
        this.check(join.threshold() >= 1, "alts.join-notification.threshold",
                "must be at least 1");
        this.check(join.limit() >= 1, "alts.join-notification.limit", "must be at least 1");
        final var limiter = alts.connectionLimiter();
        this.check(limiter.limit() >= 1, "alts.connection-limiter.limit", "must be at least 1");
        return new Settings.Alts(alts.registerOnConnection(), Set.copyOf(servers),
                this.durationOrNever("alts.record-expiration", alts.recordExpiration())
                        .orElse(null),
                new Settings.JoinNotification(join.enabled(), join.onlyIfPunished(),
                        join.threshold(), join.limit()),
                new Settings.ConnectionLimiter(limiter.enabled(), limiter.limit(),
                        limiter.window()));
    }

    private Settings.Reasons reasons(final ReasonsSection reasons) {
        this.check(reasons.defaultReason().length() <= Lengths.MAX_REASON, "reasons.default",
                "is longer than " + Lengths.MAX_REASON + " characters");
        final var require = reasons.require();
        final Set<PunishmentType> required = EnumSet.noneOf(PunishmentType.class);
        if (require.ban()) {
            required.add(PunishmentType.BAN);
        }
        if (require.mute()) {
            required.add(PunishmentType.MUTE);
        }
        if (require.warn()) {
            required.add(PunishmentType.WARN);
        }
        if (require.kick()) {
            required.add(PunishmentType.KICK);
        }
        final Map<String, String> shortcuts = new HashMap<>();
        reasons.shortcuts().forEach((name, text) -> {
            final String key = "reasons.shortcuts." + name;
            final String normalized = Validator.normalizeIdentifier(name).orElse(null);
            this.check(normalized != null, key, "is not a name (1-64 of a-z, 0-9, _ and -)");
            this.check(!text.isBlank() && text.length() <= Lengths.MAX_REASON, key,
                    "must be a reason of 1-" + Lengths.MAX_REASON + " characters");
            if (normalized != null) {
                shortcuts.put(normalized, text.strip());
            }
        });
        return new Settings.Reasons(reasons.defaultReason(), Set.copyOf(required),
                require.revoke(), reasons.allowNewlines(), shortcuts);
    }

    private Settings.Warnings warnings(final WarningsSection warnings) {
        final Map<Integer, List<String>> actions = new HashMap<>();
        warnings.actions().forEach((count, commands) -> {
            final String path = "warnings.actions." + count;
            int parsed = 0;
            try {
                parsed = Integer.parseInt(count.strip());
            } catch (final NumberFormatException e) {
                // Reported below.
            }
            this.check(parsed >= 1, path, "must be a number of warnings (1 or more)");
            final List<String> valid = new ArrayList<>();
            for (final String command : commands) {
                final String stripped = command.strip().replaceFirst("^/", "");
                this.check(!stripped.isEmpty(), path, "has an empty command");
                if (!stripped.isEmpty()) {
                    valid.add(stripped);
                }
            }
            if (parsed >= 1) {
                actions.put(parsed, List.copyOf(valid));
            }
        });
        return new Settings.Warnings(
                this.durationOrNever("warnings.expire-after", warnings.expireAfter())
                        .orElse(null), actions);
    }

    private Settings.Display display(final DisplaySection display) {
        final Locale locale = Locale.forLanguageTag(display.defaultLocale().replace('_', '-'));
        this.check(!locale.getLanguage().isEmpty(), "display.default-locale",
                "\"" + display.defaultLocale() + "\" is not a locale (like en_US)");
        try {
            DateTimeFormatter.ofPattern(display.dateFormat());
        } catch (final IllegalArgumentException e) {
            this.error("display.date-format", "\"" + display.dateFormat()
                    + "\" is not a date pattern: " + e.getMessage());
        }
        ZoneId zone = ZoneId.systemDefault();
        if (!display.timezone().equalsIgnoreCase("default")) {
            try {
                zone = ZoneId.of(display.timezone());
            } catch (final DateTimeException e) {
                this.error("display.timezone", "\"" + display.timezone()
                        + "\" is not a time zone (like UTC or Europe/Berlin)");
            }
        }
        return new Settings.Display(locale, display.dateFormat(), zone, display.idStyle(),
                display.censorIpAddresses());
    }

    private StorageSettings mapStorage(final StorageConfig config) {
        this.check(TABLE_PREFIX.matcher(config.tablePrefix()).matches(), "table-prefix",
                "must be 1-16 characters of a-z, 0-9 and '_', starting with a letter");
        this.check(config.port() >= 0 && config.port() <= 65535, "port",
                "must be 0 (the default port) to 65535");
        this.check(!config.h2().file().isBlank(), "h2.file", "must not be blank");
        if (config.type() != StorageType.H2) {
            this.check(!config.host().isBlank(), "host", "must not be blank");
            this.check(!config.database().isBlank(), "database", "must not be blank");
        }
        final Optional<String> schema = config.schema().isBlank()
                ? Optional.empty()
                : Optional.of(config.schema());
        schema.ifPresent(name -> this.check(SQL_NAME.matcher(name).matches(), "schema",
                "must be a lower-case SQL name"));
        this.check(config.sync().mode() != SyncMode.SQL || config.type() != StorageType.H2,
                "sync.mode", "SQL sync needs a shared database (MARIADB, MYSQL or POSTGRESQL)");
        this.check(config.sync().pollInterval().compareTo(MIN_POLL) >= 0
                        && config.sync().pollInterval().compareTo(MAX_POLL) <= 0,
                "sync.poll-interval", "must be from 250ms to 30s");
        final var pool = config.pool();
        this.check(pool.maximumSize() >= 1, "pool.maximum-size", "must be at least 1");
        this.check(pool.minimumIdle() >= 0 && pool.minimumIdle() <= pool.maximumSize(),
                "pool.minimum-idle", "must be 0 to pool.maximum-size");
        return new StorageSettings(config.type(), config.h2().file(), config.host(), config.port(),
                config.database(), config.username(), config.password(), config.tablePrefix(),
                schema.orElse(null), config.properties(),
                new StorageSettings.Pool(pool.maximumSize(), pool.minimumIdle(),
                        pool.connectionTimeout(), pool.maxLifetime()),
                new StorageSettings.Sync(config.sync().mode(), config.sync().pollInterval()));
    }

    private Settings.Commands commands(final CommandsSection section) {
        final Map<String, List<String>> labels = new LinkedHashMap<>();
        section.labels().forEach((command, names) -> {
            final List<String> valid = new ArrayList<>();
            for (final String name : names) {
                final String lower = name.strip().toLowerCase(Locale.ROOT);
                if (LABEL.matcher(lower).matches()) {
                    valid.add(lower);
                } else {
                    this.error("commands.labels." + command, "\"" + name
                            + "\" must be 1-32 characters of a-z, 0-9, '_' or '-'");
                }
            }
            labels.put(command.toLowerCase(Locale.ROOT), List.copyOf(valid));
        });
        final Map<String, Settings.KindCommand> kinds = new LinkedHashMap<>();
        section.kinds().forEach((kind, command) -> {
            final String path = "commands.kinds." + kind;
            this.identifier(path, kind).ifPresent(id -> {
                final List<String> valid = new ArrayList<>();
                for (final String name : command.labels()) {
                    final String lower = name.strip().toLowerCase(Locale.ROOT);
                    if (LABEL.matcher(lower).matches()) {
                        valid.add(lower);
                    } else {
                        this.error(path + ".labels", "\"" + name
                                + "\" must be 1-32 characters of a-z, 0-9, '_' or '-'");
                    }
                }
                kinds.put(id, new Settings.KindCommand(command.type(), valid));
            });
        });
        return new Settings.Commands(labels, kinds);
    }

    private Map<String, Set<String>> categories(final Map<String, List<String>> section) {
        final Map<String, Set<String>> categories = new LinkedHashMap<>();
        section.forEach((category, servers) -> {
            final String path = "scopes.categories." + category;
            this.identifier(path, category).ifPresent(name -> {
                final Set<String> members = new LinkedHashSet<>(
                        categories.getOrDefault(name, Set.of()));
                for (final String server : servers) {
                    this.identifier(path, server).ifPresent(members::add);
                }
                categories.put(name, Set.copyOf(members));
            });
        });
        return Map.copyOf(categories);
    }

    private Settings.DefaultScope defaultScope(final String text) {
        final String value = text.strip().toLowerCase(Locale.ROOT);
        if (value.equals("global")) {
            return new Settings.DefaultScope.Fixed(GlobalScope.INSTANCE);
        }
        if (value.equals("local")) {
            return new Settings.DefaultScope.Local();
        }
        final int colon = value.indexOf(':');
        final String kind = colon < 0 ? "" : value.substring(0, colon);
        final Optional<String> name = colon < 0
                ? Optional.empty()
                : Validator.normalizeIdentifier(value.substring(colon + 1));
        if (kind.equals("server") && name.isPresent()) {
            return new Settings.DefaultScope.Fixed(ServerScope.of(name.get()));
        }
        if (kind.equals("category") && name.isPresent()) {
            return new Settings.DefaultScope.Fixed(CategoryScope.of(name.get()));
        }
        this.error("scopes.default", "\"" + text
                + "\" is not a scope (global, local, server:<name> or category:<name>)");
        return new Settings.DefaultScope.Fixed(GlobalScope.INSTANCE);
    }

    private Optional<Duration> durationOrNever(final String path, final String text) {
        if (text.strip().equalsIgnoreCase("never")) {
            return Optional.empty();
        }
        final Optional<Duration> duration = Durations.parse(text);
        if (duration.isEmpty()) {
            this.error(path, "\"" + text + "\" is not a duration (like 30d) or never");
        }
        return duration;
    }

    private Optional<String> identifier(final String path, final String value) {
        final Optional<String> identifier = Validator.normalizeIdentifier(value);
        if (identifier.isEmpty()) {
            this.error(path, "\"" + value + "\" must be 1-64 characters of a-z, 0-9, '_' or '-'");
        }
        return identifier;
    }

    private void check(final boolean valid, final String path, final String message) {
        if (!valid) {
            this.error(path, message);
        }
    }

    private void error(final String path, final String message) {
        this.errors.add(ConfigIssue.error(this.file, path, message));
    }
}
