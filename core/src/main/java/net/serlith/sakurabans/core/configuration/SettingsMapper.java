package net.serlith.sakurabans.core.configuration;

import java.time.DateTimeException;
import java.time.Duration;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
import net.serlith.sakurabans.core.configuration.file.MainConfig;
import net.serlith.sakurabans.core.configuration.file.ReasonsSection;
import net.serlith.sakurabans.core.configuration.file.StorageConfig;
import net.serlith.sakurabans.core.configuration.settings.Settings;
import net.serlith.sakurabans.core.configuration.settings.StorageSettings;
import net.serlith.sakurabans.core.configuration.settings.StorageType;
import net.serlith.sakurabans.core.configuration.settings.SyncMode;
import net.serlith.sakurabans.core.util.Durations;
import org.jspecify.annotations.Nullable;

final class SettingsMapper {

    private static final Duration MIN_POLL = Duration.ofMillis(250);
    private static final Duration MAX_POLL = Duration.ofSeconds(30);
    private static final Pattern TABLE_PREFIX = Pattern.compile("[a-z][a-z0-9_]{0,15}");
    private static final Pattern SQL_NAME = Pattern.compile("[a-z_][a-z0-9_]{0,62}");
    private static final Duration MAX_LOGIN_TIMEOUT = Duration.ofSeconds(30);
    private static final Pattern LABEL = Pattern.compile("[a-z0-9_-]{1,32}");

    private final String file;
    private final List<ConfigIssue> errors = new ArrayList<>();

    private SettingsMapper(final String file) {
        this.file = file;
    }

    record Mapped<T>(@Nullable T value, List<ConfigIssue> errors) {
    }

    static Mapped<Settings> settings(final MainConfig config) {
        final SettingsMapper mapper = new SettingsMapper(ConfigFiles.CONFIG);
        final Settings settings = mapper.mapSettings(config);
        return new Mapped<>(mapper.errors.isEmpty() ? settings : null, List.copyOf(mapper.errors));
    }

    static Mapped<StorageSettings> storage(final StorageConfig config) {
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
        final Settings.Warnings warnings = new Settings.Warnings(
                this.durationOrNever("warnings.expire-after", config.warnings().expireAfter())
                        .orElse(null));
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
        return new Settings.Reasons(reasons.defaultReason(), Set.copyOf(required),
                require.revoke(), reasons.allowNewlines());
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
        return new Settings.Commands(labels);
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
