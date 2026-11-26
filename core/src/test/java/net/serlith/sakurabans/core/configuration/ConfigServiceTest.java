package net.serlith.sakurabans.core.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
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
                assertThat(Files.readAllBytes(this.dir.resolve(name)))
                        .isEqualTo(shipped.readAllBytes());
            }
        }
        assertThat(this.log.entries("warn")).as("no unknown or missing keys").isEmpty();
        assertThat(service.settings())
                .isEqualTo(SettingsMapper.settings(new MainConfig()).value());
        assertThat(service.storage())
                .isEqualTo(SettingsMapper.storage(new StorageConfig()).value());
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
        assertThat(settings.enforcement().muteCommandBlacklist()).containsOnly("msg", "tell");
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
    void addressHashesNeedNoKey() throws Exception {
        try (InputStream shipped = ConfigFiles.defaultResource(ConfigFiles.STORAGE)) {
            assertThat(new String(shipped.readAllBytes(), StandardCharsets.UTF_8))
                    .doesNotContain("address-hash-key");
        }
        this.write(ConfigFiles.STORAGE, "address-hash-key: secret\n");
        final ConfigService service = this.service();

        service.load();

        assertThat(this.log.entries("warn")).extracting(RecordingLogger.Entry::format)
                .anyMatch(text -> text.contains("address-hash-key")
                        && text.contains("unknown key"));
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
        this.write(ConfigFiles.STORAGE, "type: POSTGRESQL\nreasons: {}\n");
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
        this.write(ConfigFiles.STORAGE, "password: hunter2\n");
        final ConfigService service = this.service();

        service.load();

        assertThat(service.storage().password()).isEqualTo("hunter2");
        assertThat(service.storage().toString()).doesNotContain("hunter2");
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
}
