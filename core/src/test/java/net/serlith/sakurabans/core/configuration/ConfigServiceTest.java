package net.serlith.sakurabans.core.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import net.serlith.sakurabans.api.model.AddressStrictness;
import net.serlith.sakurabans.api.model.CategoryScope;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.core.RecordingLogger;
import net.serlith.sakurabans.core.configuration.file.MainConfig;
import net.serlith.sakurabans.core.configuration.file.StorageConfig;
import net.serlith.sakurabans.core.configuration.settings.Settings;
import net.serlith.sakurabans.core.configuration.settings.StorageType;
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

        for (final String name : List.of(ConfigFiles.CONFIG, ConfigFiles.STORAGE)) {
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
        assertThat(settings.server().name()).contains("lobby-1");
        assertThat(settings.server().categories()).containsExactly("survival");
        assertThat(settings.scopes().defaultScope())
                .isEqualTo(new Settings.DefaultScope.Fixed(CategoryScope.of("survival")));
        assertThat(settings.enforcement().addressStrictness()).isEqualTo(AddressStrictness.STERN);
        assertThat(settings.enforcement().loginCheckTimeout()).isEqualTo(Duration.ofSeconds(2));
        assertThat(settings.enforcement().muteCommandBlacklist()).containsOnly("msg", "tell");
        assertThat(settings.reasons().requiredFor()).containsExactly(PunishmentType.BAN);
        assertThat(settings.warnings().expireAfter()).isEmpty();
        assertThat(Files.readString(this.dir.resolve(ConfigFiles.CONFIG))).isEqualTo(yaml);
        assertThat(this.log.entries("warn")).singleElement()
                .satisfies(entry -> assertThat(entry.format()).contains("missing keys"));
    }

    @Test
    void everyErrorIsReportedWithItsPath() throws Exception {
        this.write(ConfigFiles.CONFIG,
                """
                server:
                  name: not a name
                  role: sideways
                scopes:
                  default: everywhere
                enforcement:
                  login-check-timeout: 10x
                alts:
                  connection-limiter:
                    limit: lots
                    window: 1m
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
                                "config.yml: server.role",
                                "config.yml: enforcement.login-check-timeout",
                                "config.yml: alts.connection-limiter.limit",
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
                    .containsExactlyInAnyOrder(ConfigFiles.CONFIG, ConfigFiles.STORAGE);
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
    void theStoragePasswordNeverReachesToString() throws Exception {
        this.write(ConfigFiles.STORAGE, "password: hunter2\n");
        final ConfigService service = this.service();

        service.load();

        assertThat(service.storage().password()).isEqualTo("hunter2");
        assertThat(service.storage().toString()).doesNotContain("hunter2");
    }

    @Test
    void settingsAreUnavailableBeforeLoading() {
        assertThatThrownBy(this.service()::settings).isInstanceOf(IllegalStateException.class);
    }
}
