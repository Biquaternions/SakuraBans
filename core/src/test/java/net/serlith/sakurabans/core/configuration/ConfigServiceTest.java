package net.serlith.sakurabans.core.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.serlith.sakurabans.api.model.AddressStrictness;
import net.serlith.sakurabans.api.model.CategoryScope;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.core.RecordingLogger;
import net.serlith.sakurabans.core.configuration.file.ImportConfig;
import net.serlith.sakurabans.core.configuration.file.LimitsConfig;
import net.serlith.sakurabans.core.configuration.file.MainConfig;
import net.serlith.sakurabans.core.configuration.file.StorageConfig;
import net.serlith.sakurabans.core.configuration.file.WebhooksConfig;
import net.serlith.sakurabans.core.configuration.settings.ImportSettings;
import net.serlith.sakurabans.core.configuration.settings.Settings;
import net.serlith.sakurabans.core.configuration.settings.StorageType;
import net.serlith.sakurabans.core.configuration.settings.WebhookSettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigServiceTest {

    @TempDir
    private Path dir;

    private static final String KEY = "config-test-address-hash-key-0123456789";
    private static final String KEY_LINE = "address-hash-key: '" + KEY + "'\n";
    private static final Pattern GENERATED =
            Pattern.compile("\naddress-hash-key: '([A-Za-z0-9_-]{43})'\n");

    private final RecordingLogger log = new RecordingLogger();

    private ConfigService service() {
        return new ConfigService(this.dir, this.log.logger());
    }

    private void write(final String name, final String yaml) throws Exception {
        Files.createDirectories(this.dir);
        Files.writeString(this.dir.resolve(name), yaml);
    }

    private static String errorsOf(final ConfigException e) {
        return String.join("\n", e.errors().stream().map(ConfigIssue::toString).toList());
    }

    @Test
    void theShippedDefaultsAreCopiedAsTheyAreAndMatchTheModels() throws Exception {
        final ConfigService service = this.service();

        service.load();

        for (final String name : List.of(ConfigFiles.CONFIG, ConfigFiles.STORAGE,
                ConfigFiles.LIMITS, ConfigFiles.WEBHOOKS)) {
            try (InputStream shipped = ConfigFiles.defaultResource(name)) {
                final String copied = Files.readString(this.dir.resolve(name));
                // storage.yml gets a generated address-hash-key, and only that changes.
                assertThat(GENERATED.matcher(copied).replaceFirst(
                        "\n" + AddressHashKeys.EMPTY_LINE + "\n"))
                        .isEqualTo(new String(shipped.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        assertThat(this.log.entries("warn")).as("no unknown or missing keys").isEmpty();
        assertThat(service.settings())
                .isEqualTo(SettingsMapper.settings(new MainConfig()).value());
        assertThat(SettingsMapper.storage(new StorageConfig()).errors())
                .extracting(ConfigIssue::path).containsExactly("address-hash-key");
        final Path keyOnly = this.dir.resolve("key-only");
        Files.createDirectories(keyOnly);
        Files.writeString(keyOnly.resolve(ConfigFiles.STORAGE), KEY_LINE);
        final ConfigService defaults = new ConfigService(keyOnly, new RecordingLogger().logger());
        defaults.load();
        assertThat(service.storage()).usingRecursiveComparison().ignoringFields("addressHashKey")
                .isEqualTo(defaults.storage());
        assertThat(service.limits())
                .isEqualTo(SettingsMapper.limits(new LimitsConfig()).value());
        assertThat(service.webhooks())
                .isEqualTo(SettingsMapper.webhooks(new WebhooksConfig()).value());
        assertThat(service.webhooks().routes()).containsOnlyKeys(WebhookSettings.ROUTES);
    }

    @Test
    void anEditedFileIsAppliedAndNeverRewritten() throws Exception {
        final String yaml =
                """
                # my own comment
                server:
                  name: Lobby-1
                  categories: [survival]
                scopes:
                  default: category:survival
                enforcement:
                  address-strictness: stern
                  login-check-timeout: 2s
                  mute-command-blacklist: [/MSG, tell]
                reasons:
                  require:
                    ban: true
                warnings:
                  expire-after: never
                """;
        this.write(ConfigFiles.CONFIG, yaml);
        final ConfigService service = this.service();

        service.load();

        final Settings settings = service.settings();
        assertThat(settings.server().name()).isEqualTo("lobby-1");
        assertThat(settings.server().categories()).containsExactly("survival");
        assertThat(settings.scopes().defaultScope())
                .isEqualTo(new Settings.DefaultScope.Fixed(CategoryScope.of("survival")));
        assertThat(settings.enforcement().addressStrictness()).isEqualTo(AddressStrictness.STERN);
        assertThat(settings.enforcement().loginCheckTimeout()).isEqualTo(Duration.ofSeconds(2));
        assertThat(settings.enforcement().muteCommandBlacklist().entries())
                .containsOnly("msg", "tell");
        assertThat(settings.reasons().requiredFor()).containsExactly(PunishmentType.BAN);
        assertThat(settings.warnings().expireAfter()).isNull();
        assertThat(Files.readString(this.dir.resolve(ConfigFiles.CONFIG))).isEqualTo(yaml);
        assertThat(this.log.entries("warn")).singleElement()
                .satisfies(entry -> assertThat(entry.format()).contains("missing keys"));
    }

    @Test
    void theEditorAddressIsHttpsOrLocalAndTheTokenStaysSecret() throws Exception {
        for (final String url : List.of("https://editor.example.org/", "http://127.0.0.1:3000",
                "http://192.168.1.5", "http://[::1]:8080", "http://localhost", "")) {
            this.write(ConfigFiles.CONFIG, "web:\n  url: '" + url + "'\n  token: hunter2\n");
            final ConfigService service = this.service();
            service.load();
            assertThat(service.settings().web().url()).as(url).satisfies(parsed -> {
                if (url.isEmpty()) {
                    assertThat(parsed).isNull();
                } else {
                    assertThat(parsed).isNotNull();
                    assertThat(parsed.toString()).doesNotEndWith("/");
                }
            });
            assertThat(service.settings().toString()).doesNotContain("hunter2");
        }
        for (final String url : List.of("http://editor.example.org", "ftp://example.org",
                "editor.example.org", "https://example.org/?x=1")) {
            this.write(ConfigFiles.CONFIG, "web:\n  url: '" + url + "'\n");
            assertThatThrownBy(this.service()::load).as(url)
                    .isInstanceOfSatisfying(ConfigException.class, e -> assertThat(e.errors())
                            .extracting(issue -> issue.path()).containsExactly("web.url"));
        }
    }

    @Test
    void sqlSyncNeedsSharedDatabase() throws Exception {
        this.write(ConfigFiles.STORAGE, "sync:\n  mode: SQL\n  poll-interval: 1m\n");

        assertThatThrownBy(this.service()::load)
                .isInstanceOfSatisfying(ConfigException.class, e -> assertThat(e.errors())
                        .extracting(issue -> issue.file() + ": " + issue.path())
                        .contains("storage.yml: sync.mode", "storage.yml: sync.poll-interval"));
    }

    @Test
    void everyErrorIsReportedWithItsPath() throws Exception {
        this.write(ConfigFiles.CONFIG,
                """
                server:
                  name: not a name
                scopes:
                  default: everywhere
                enforcement:
                  login-check-timeout: 10x
                alts:
                  join-notification: oops
                display:
                  timezone: Mars/Olympus
                  date-format: dd MMM yyyy HH:mm bbb
                """);
        this.write(ConfigFiles.STORAGE,
                """
                table-prefix: 1bad
                pool:
                  maximum-size: 0
                """);

        assertThatThrownBy(this.service()::load)
                .isInstanceOfSatisfying(ConfigException.class, e -> assertThat(e.errors())
                        .extracting(issue -> issue.file() + ": " + issue.path())
                        .contains(
                                "config.yml: enforcement.login-check-timeout",
                                "config.yml: alts.join-notification",
                                "storage.yml: pool.maximum-size",
                                "storage.yml: table-prefix"));
    }

    @Test
    void valueRulesAreReportedOnceTheTypesAreRight() throws Exception {
        this.write(ConfigFiles.CONFIG,
                """
                server:
                  name: not a name
                scopes:
                  default: everywhere
                display:
                  timezone: Mars/Olympus
                  date-format: dd MMM yyyy HH:mm bbb
                """);

        assertThatThrownBy(this.service()::load)
                .isInstanceOfSatisfying(ConfigException.class, e -> assertThat(e.errors())
                        .extracting(ConfigIssue::path)
                        .containsExactlyInAnyOrder("server.name", "scopes.default",
                                "display.timezone", "display.date-format"));
    }

    @Test
    void policyListsAreParsedAndBadEntriesNamed() throws Exception {
        this.write(ConfigFiles.CONFIG,
                """
                enforcement:
                  servers-without-login-check: [Limbo, appeal]
                  network-kick: true
                alts:
                  hidden-players: [Notch, 069a79f4-44e9-4726-a5be-fca90e38aaf5, 203.0.113.7,
                    10.0.0.0/8]
                exemption:
                  players: [Steve:mute, 069a79f444e94726a5befca90e38aaf5]
                  groups: [Admin]
                """);
        final ConfigService service = this.service();
        service.load();
        final Settings settings = service.settings();
        assertThat(settings.enforcement().serversWithoutLoginCheck())
                .containsExactly("limbo", "appeal");
        assertThat(settings.enforcement().loginCheck()).isTrue();
        assertThat(settings.enforcement().networkKick()).isTrue();
        final Settings.HiddenPlayers hidden = settings.alts().hiddenPlayers();
        assertThat(hidden.hidesPlayer(java.util.UUID.randomUUID(), "notch")).isTrue();
        assertThat(hidden.hidesPlayer(java.util.UUID.fromString(
                "069a79f4-44e9-4726-a5be-fca90e38aaf5"), "x")).isTrue();
        assertThat(hidden.hidesAddress(java.net.InetAddress.getByName("10.1.2.3"))).isTrue();
        assertThat(hidden.hidesAddress(java.net.InetAddress.getByName("203.0.113.7"))).isTrue();
        assertThat(hidden.hidesAddress(java.net.InetAddress.getByName("203.0.113.8"))).isFalse();
        assertThat(settings.exemption().players()).hasSize(2);
        assertThat(settings.exemption().groups()).containsExactly("admin");

        this.write(ConfigFiles.CONFIG,
                """
                enforcement:
                  servers-without-login-check: [not a server]
                alts:
                  hidden-players: [not a name or address]
                exemption:
                  players: [Steve:forever, "two words"]
                  groups: ["bad group"]
                """);
        assertThatThrownBy(this.service()::load)
                .isInstanceOfSatisfying(ConfigException.class, e -> {
                    assertThat(e.errors()).extracting(ConfigIssue::path)
                            .containsExactlyInAnyOrder(
                                    "enforcement.servers-without-login-check",
                                    "alts.hidden-players", "exemption.players",
                                    "exemption.players", "exemption.groups");
                    assertThat(errorsOf(e)).as("a hidden entry may be an address")
                            .doesNotContain("not a name or address");
                });
    }

    @Test
    void malformedFileRefusesToLoadAndIsLeftAlone() throws Exception {
        final String broken = "server: [unclosed\n  name: lobby\n";
        this.write(ConfigFiles.CONFIG, broken);

        assertThatThrownBy(this.service()::load)
                .isInstanceOfSatisfying(ConfigException.class, e ->
                        assertThat(errorsOf(e)).contains("config.yml"));
        assertThat(Files.readString(this.dir.resolve(ConfigFiles.CONFIG))).isEqualTo(broken);
        try (var files = Files.list(this.dir)) {
            assertThat(files.map(path -> path.getFileName().toString()))
                    .containsExactlyInAnyOrder(ConfigFiles.CONFIG, ConfigFiles.STORAGE,
                            ConfigFiles.LIMITS, ConfigFiles.WEBHOOKS);
        }
    }

    @Test
    void unknownKeysAreWarnings() throws Exception {
        this.write(ConfigFiles.CONFIG, "sever:\n  name: lobby\n");
        final ConfigService service = this.service();

        service.load();

        assertThat(this.log.entries("warn")).extracting(RecordingLogger.Entry::format)
                .anyMatch(text -> text.contains("sever") && text.contains("unknown key"));
    }

    @Test
    void freshInstallsGetGeneratedAddressHashKeysThatAreNeverLogged() throws Exception {
        final ConfigService service = this.service();

        service.load();

        final Matcher generated = GENERATED.matcher(
                Files.readString(this.dir.resolve(ConfigFiles.STORAGE)));
        assertThat(generated.find()).isTrue();
        final String key = generated.group(1);
        assertThat(service.storage().addressHashKey()).isEqualTo(key);
        assertThat(service.storage().toString()).doesNotContain(key);
        assertThat(this.log.entries("info")).extracting(RecordingLogger.Entry::format)
                .anyMatch(text -> text.contains("new address-hash-key")
                        && text.contains("copy that line"));
        assertThat(java.util.stream.Stream.of("trace", "debug", "info", "warn", "error")
                .flatMap(level -> this.log.entries(level).stream())
                .map(RecordingLogger.Entry::format)).noneMatch(text -> text.contains(key));

        final Path other = this.dir.resolve("other");
        final ConfigService second = new ConfigService(other, this.log.logger());
        second.load();
        assertThat(second.storage().addressHashKey()).as("each install its own")
                .isNotEqualTo(key).hasSize(43);
    }

    @Test
    void existingFilesWithoutKeysAreErrorsAndAreNotRewritten() throws Exception {
        this.write(ConfigFiles.STORAGE, "type: H2\n");

        assertThatThrownBy(this.service()::load)
                .isInstanceOfSatisfying(ConfigException.class, e -> assertThat(e.errors())
                        .singleElement().satisfies(issue -> {
                            assertThat(issue.path()).isEqualTo("address-hash-key");
                            assertThat(issue.message()).contains("must be set",
                                    "copy address-hash-key from the storage.yml of a server",
                                    "at least 32 characters");
                        }));
        assertThat(Files.readString(this.dir.resolve(ConfigFiles.STORAGE)))
                .isEqualTo("type: H2\n");

        this.write(ConfigFiles.STORAGE, "address-hash-key: too-short\n");
        assertThatThrownBy(this.service()::load)
                .isInstanceOfSatisfying(ConfigException.class, e -> assertThat(errorsOf(e))
                        .contains("address-hash-key", "at least 32 characters")
                        .doesNotContain("too-short"));
    }

    @Test
    void failedReloadKeepsTheOldSettings() throws Exception {
        final ConfigService service = this.service();
        service.load();
        final Settings before = service.settings();
        this.write(ConfigFiles.CONFIG, "enforcement:\n  login-check-timeout: 1y\n");

        final ReloadResult result = service.reload();

        assertThat(result).isInstanceOfSatisfying(ReloadResult.Failed.class, failed ->
                assertThat(failed.errors()).extracting(ConfigIssue::path)
                        .containsExactly("enforcement.login-check-timeout"));
        assertThat(service.settings()).isSameAs(before);
    }

    @Test
    void storageChangesWaitForRestart() throws Exception {
        final ConfigService service = this.service();
        service.load();
        this.write(ConfigFiles.STORAGE, "type: POSTGRESQL\nreasons: {}\n" + KEY_LINE);
        this.write(ConfigFiles.CONFIG, "reasons:\n  default: Rule breaking\n");

        final ReloadResult result = service.reload();

        assertThat(result).isInstanceOfSatisfying(ReloadResult.Reloaded.class, reloaded ->
                assertThat(reloaded.restartRequired()).containsExactly(ConfigFiles.STORAGE));
        assertThat(service.storage().type()).isEqualTo(StorageType.H2);
        assertThat(service.settings().reasons().defaultReason()).isEqualTo("Rule breaking");
    }

    @Test
    void commandChangesWaitForRestart() throws Exception {
        final ConfigService service = this.service();
        service.load();
        this.write(ConfigFiles.CONFIG, "commands:\n  labels:\n    ban: [ban, b]\n");

        assertThat(service.reload()).isInstanceOfSatisfying(ReloadResult.Reloaded.class,
                reloaded -> assertThat(reloaded.restartRequired())
                        .containsExactly("commands.labels"));

        this.write(ConfigFiles.CONFIG, "reasons:\n  default: Rule breaking\n");
        service.reload();
        this.write(ConfigFiles.CONFIG, "reasons:\n  default: Other\n");
        assertThat(service.reload()).isInstanceOfSatisfying(ReloadResult.Reloaded.class,
                reloaded -> assertThat(reloaded.restartRequired()).isEmpty());
    }

    @Test
    void placeholderRefreshHasFloor() throws Exception {
        final ConfigService service = this.service();
        service.load();
        assertThat(service.settings().placeholders().refresh()).isEqualTo(Duration.ofMinutes(2));

        this.write(ConfigFiles.CONFIG, "placeholders:\n  refresh: 30s\n");
        service.reload();
        assertThat(service.settings().placeholders().refresh())
                .isEqualTo(Duration.ofSeconds(30));

        this.write(ConfigFiles.CONFIG, "placeholders:\n  refresh: 10s\n");
        assertThat(service.reload()).isInstanceOfSatisfying(ReloadResult.Failed.class,
                failed -> assertThat(failed.errors()).extracting(ConfigIssue::path)
                        .containsExactly("placeholders.refresh"));
        assertThat(service.settings().placeholders().refresh())
                .as("the old settings stay").isEqualTo(Duration.ofSeconds(30));
    }

    @Test
    void theAttemptThrottleMayBeZeroButNothingElseMay() throws Exception {
        final ConfigService service = this.service();
        service.load();
        assertThat(service.settings().notifications().attemptThrottle())
                .isEqualTo(Duration.ofSeconds(30));

        this.write(ConfigFiles.CONFIG, "notifications:\n  attempt-throttle: 0s\n");
        service.reload();
        assertThat(service.settings().notifications().attemptThrottle()).isEqualTo(Duration.ZERO);

        this.write(ConfigFiles.CONFIG, "notifications:\n  attempt-throttle: soon\n");
        assertThat(service.reload()).isInstanceOfSatisfying(ReloadResult.Failed.class,
                failed -> assertThat(failed.errors()).extracting(ConfigIssue::path)
                        .containsExactly("notifications.attempt-throttle"));

        this.write(ConfigFiles.CONFIG, "enforcement:\n  login-check-timeout: 0s\n");
        assertThat(service.reload()).isInstanceOf(ReloadResult.Failed.class);
    }

    @Test
    void theStoragePasswordNeverReachesToString() throws Exception {
        this.write(ConfigFiles.STORAGE, "password: hunter2\n" + KEY_LINE);
        final ConfigService service = this.service();

        service.load();

        assertThat(service.storage().password()).isEqualTo("hunter2");
        assertThat(service.storage().toString()).doesNotContain("hunter2").doesNotContain(KEY);
    }

    @Test
    void importYmlIsReadOnlyWhenAskedAndMatchesItsModel() throws Exception {
        final ConfigService service = this.service();
        service.load();
        assertThat(this.dir.resolve(ConfigFiles.IMPORT)).doesNotExist();

        final ImportSettings imports = service.imports();

        try (InputStream shipped = ConfigFiles.defaultResource(ConfigFiles.IMPORT)) {
            assertThat(Files.readAllBytes(this.dir.resolve(ConfigFiles.IMPORT)))
                    .isEqualTo(shipped.readAllBytes());
        }
        assertThat(this.log.entries("warn")).as("no unknown or missing keys").isEmpty();
        assertThat(imports).isEqualTo(SettingsMapper.imports(new ImportConfig()).value());
        assertThat(imports.databases().keySet()).isEqualTo(ImportSettings.DATABASES);
        assertThat(imports.database(ImportSettings.LITEBANS).orElseThrow().tablePrefix())
                .isEqualTo("litebans_");
        assertThat(imports.vanilla().bannedIps()).isEqualTo(Path.of("banned-ips.json"));
    }

    @Test
    void importYmlErrorsAndPasswords() throws Exception {
        final String yaml =
                """
                libertybans:
                  jdbc-url: 'jdbc:sqlite:bans.db'
                  password: hunter2
                  table-prefix: 'no spaces'
                banmanager:
                  password: hunter2
                """;
        this.write(ConfigFiles.IMPORT, yaml);
        final ConfigService service = this.service();

        assertThatThrownBy(service::imports).isInstanceOfSatisfying(ConfigException.class, e ->
                assertThat(errorsOf(e)).contains("libertybans.jdbc-url",
                        "libertybans.table-prefix"));

        this.write(ConfigFiles.IMPORT, "banmanager:\n  password: hunter2\n");
        final ImportSettings imports = service.imports();
        assertThat(imports.database(ImportSettings.BANMANAGER).orElseThrow().password())
                .isEqualTo("hunter2");
        assertThat(imports.toString()).doesNotContain("hunter2");
        assertThat(new ImportConfig().banmanager().toString()).contains("password=***");
    }

    @Test
    void settingsAreUnavailableBeforeLoading() {
        assertThatThrownBy(this.service()::settings).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void altsAndWarnsAnswerToTheirLiteBansNamesUnlessTheFileListsTheirLabels()
            throws Exception {
        final ConfigService fresh = this.service();
        fresh.load();
        assertThat(fresh.settings().commands().labels("alts")).containsExactly("alts", "dupeip");
        assertThat(fresh.settings().commands().labels("warns"))
                .containsExactly("warns", "warnings");
        assertThat(fresh.settings().commands().labels("ban")).containsExactly("ban");

        // An older file that doesn't list them still gets the aliases.
        this.write(ConfigFiles.CONFIG, "commands:\n  labels:\n    sakurabans: [sb]\n");
        final ConfigService older = this.service();
        older.load();
        assertThat(older.settings().commands().labels("alts")).containsExactly("alts", "dupeip");
        assertThat(older.settings().commands().labels("sakurabans")).containsExactly("sb");

        // Listing a command replaces its labels, and [] turns it off.
        this.write(ConfigFiles.CONFIG,
                "commands:\n  labels:\n    alts: [alts]\n    warns: []\n");
        final ConfigService listed = this.service();
        listed.load();
        assertThat(listed.settings().commands().labels("alts")).containsExactly("alts");
        assertThat(listed.settings().commands().labels("warns")).isEmpty();
    }

    @Test
    void kickIgnoredReasonsAreCaseInsensitiveSubstringsAndNeverEmpty() throws Exception {
        final ConfigService defaults = this.service();
        defaults.load();
        final Settings.Enforcement shipped = defaults.settings().enforcement();
        assertThat(shipped.kickIgnoredReasons()).containsExactly("restarting", "afk");
        assertThat(shipped.kickReasonIgnored("Server RESTARTING soon")).isTrue();
        assertThat(shipped.kickReasonIgnored("Away from keyboard (AFK)")).isTrue();
        assertThat(shipped.kickReasonIgnored("griefing")).isFalse();

        this.write(ConfigFiles.CONFIG, "enforcement:\n  kick-ignored-reasons: [' Lag ']\n");
        final ConfigService custom = this.service();
        custom.load();
        assertThat(custom.settings().enforcement().kickReasonIgnored("so much LAG")).isTrue();
        assertThat(custom.settings().enforcement().kickReasonIgnored("afk")).isFalse();

        this.write(ConfigFiles.CONFIG, "enforcement:\n  kick-ignored-reasons: [afk, ' ']\n");
        assertThatThrownBy(this.service()::load)
                .isInstanceOfSatisfying(ConfigException.class, e -> assertThat(e.errors())
                        .extracting(issue -> issue.path())
                        .containsExactly("enforcement.kick-ignored-reasons"));
    }

    @Test
    void blacklistEntriesAreOneLabelWithOptionalWildcards() throws Exception {
        this.write(ConfigFiles.CONFIG,
                "enforcement:\n  mute-command-blacklist: ['me you', 'ok']\n");

        assertThatThrownBy(this.service()::load)
                .isInstanceOfSatisfying(ConfigException.class, e -> assertThat(e.errors())
                        .extracting(issue -> issue.path())
                        .containsExactly("enforcement.mute-command-blacklist"));
    }

    @Test
    void thereIsNoUpdateCheck() {
        // Removed on 2026-11-27 (S68, the user's decision): no updates.* keys of any kind.
        assertThat(SettingsSchema.ENTRIES).extracting(SettingsSchema.Entry::key)
                .noneMatch(key -> key.startsWith("updates."));
    }

    @Test
    void theCooldownsOfTheLimitsFileDefaultToOneSecondAndMayBeZero() throws Exception {
        final ConfigService fresh = this.service();
        fresh.load();
        assertThat(fresh.limits().consoleCooldown()).isEqualTo(Duration.ofSeconds(1));

        this.write(ConfigFiles.LIMITS,
                """
                reduce-to-limit: true
                console-cooldown: 0
                groups:
                  a:
                    permission: x.a
                  b:
                    permission: x.b
                    cooldown-redo: 0s
                  c:
                    permission: x.c
                    cooldown-redo: 20s
                """);
        final ConfigService custom = this.service();
        custom.load();
        assertThat(custom.limits().consoleCooldown()).isEqualTo(Duration.ZERO);
        assertThat(custom.limits().groups()).extracting(group -> group.cooldownRedo())
                .containsExactly(Duration.ofSeconds(1), Duration.ZERO, Duration.ofSeconds(20));

        this.write(ConfigFiles.LIMITS,
                "reduce-to-limit: true\nconsole-cooldown: soon\ngroups: {}\n");
        assertThatThrownBy(this.service()::load)
                .isInstanceOfSatisfying(ConfigException.class, e -> assertThat(e.errors())
                        .extracting(issue -> issue.path()).containsExactly("console-cooldown"));
    }

}
