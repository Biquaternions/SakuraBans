package net.serlith.sakurabans.core.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import net.serlith.sakurabans.core.RecordingLogger;
import net.serlith.sakurabans.core.configuration.settings.StorageType;
import net.serlith.sakurabans.core.configuration.settings.SyncMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** S71: the environment through {@code ConfigService}, from a map the platform would give. */
class ConfigServiceEnvironmentTest {

    private static final String KEY = "environment-address-hash-key-0123456789";
    private static final String FILE_KEY = "file-address-hash-key-0123456789-abcdef";

    @TempDir
    private Path dir;

    private final RecordingLogger log = new RecordingLogger();

    private ConfigService service(final Map<String, String> environment) {
        return new ConfigService(this.dir, this.log.logger(), environment);
    }

    private void write(final String name, final String yaml) throws Exception {
        Files.writeString(this.dir.resolve(name), yaml);
    }

    private static Map<String, String> env(final String... pairs) {
        final Map<String, String> environment = new HashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            environment.put(pairs[i], pairs[i + 1]);
        }
        return environment;
    }

    private List<String> logged() {
        return Stream.of("trace", "debug", "info", "warn", "error")
                .flatMap(level -> this.log.entries(level).stream())
                .map(RecordingLogger.Entry::format).toList();
    }

    @Test
    void theEnvironmentWinsOverTheFilesAndTheFilesAreNeverRewritten() throws Exception {
        final String config = "# mine\nserver:\n  name: from-file\n  categories: [old]\n"
                + "web:\n  token: file-token\n";
        final String storage = "# mine too\ntype: H2\nhost: file-host\naddress-hash-key: '"
                + FILE_KEY + "'\npool:\n  maximum-size: 3\n";
        this.write(ConfigFiles.CONFIG, config);
        this.write(ConfigFiles.STORAGE, storage);
        final ConfigService service = this.service(env(
                "SAKURABANS_SERVER_NAME", "Lobby-2",
                "SAKURABANS_SERVER_CATEGORIES", "survival,pvp",
                "SAKURABANS_WEB_TOKEN", "env-token",
                "SAKURABANS_STORAGE_TYPE", "mariadb",
                "SAKURABANS_STORAGE_HOST", "db.example.org",
                "SAKURABANS_STORAGE_PORT", "3307",
                "SAKURABANS_STORAGE_PASSWORD", "s3cret-pass",
                "SAKURABANS_STORAGE_POOL_MAXIMUM_SIZE", "12",
                "SAKURABANS_STORAGE_POOL_CONNECTION_TIMEOUT", "8s",
                "SAKURABANS_STORAGE_SYNC_MODE", "SQL",
                "SAKURABANS_STORAGE_SYNC_POLL_INTERVAL", "500ms",
                "SAKURABANS_STORAGE_PROPERTIES", "{\"useSSL\": \"true\"}",
                "SAKURABANS_STORAGE_ADDRESS_HASH_KEY", KEY));

        service.load();

        assertThat(service.settings().server().name()).isEqualTo("lobby-2");
        assertThat(service.settings().server().categories()).containsExactly("survival", "pvp");
        assertThat(service.settings().web().token()).isEqualTo("env-token");
        assertThat(service.storage().type()).isEqualTo(StorageType.MARIADB);
        assertThat(service.storage().host()).isEqualTo("db.example.org");
        assertThat(service.storage().port()).isEqualTo(3307);
        assertThat(service.storage().password()).isEqualTo("s3cret-pass");
        assertThat(service.storage().pool().maximumSize()).isEqualTo(12);
        assertThat(service.storage().pool().connectionTimeout()).isEqualTo(Duration.ofSeconds(8));
        assertThat(service.storage().sync().mode()).isEqualTo(SyncMode.SQL);
        assertThat(service.storage().sync().pollInterval()).isEqualTo(Duration.ofMillis(500));
        assertThat(service.storage().properties()).containsEntry("useSSL", "true");
        assertThat(service.storage().addressHashKey()).isEqualTo(KEY);
        assertThat(Files.readString(this.dir.resolve(ConfigFiles.CONFIG))).isEqualTo(config);
        assertThat(Files.readString(this.dir.resolve(ConfigFiles.STORAGE))).isEqualTo(storage);
        // A key the environment doesn't set keeps the file's value.
        assertThat(service.storage().h2File()).isEqualTo("sakurabans");
    }

    @Test
    void noFileAtAllWorksFromTheEnvironmentAlone() throws Exception {
        final ConfigService service = this.service(env(
                "SAKURABANS_STORAGE_ADDRESS_HASH_KEY", KEY, "SAKURABANS_SERVER_NAME", "proxy-a"));

        service.load();

        assertThat(service.settings().server().name()).isEqualTo("proxy-a");
        assertThat(service.storage().addressHashKey()).isEqualTo(KEY);
        assertThat(service.storage().type()).isEqualTo(StorageType.H2);
    }

    @Test
    void freshInstallsWithTheKeyInTheEnvironmentGenerateNoneIntoTheFile() throws Exception {
        final ConfigService service = this.service(env(
                "SAKURABANS_STORAGE_ADDRESS_HASH_KEY", KEY));

        service.load();

        final String created = Files.readString(this.dir.resolve(ConfigFiles.STORAGE));
        assertThat(created).contains("\n" + AddressHashKeys.EMPTY_LINE + "\n")
                .doesNotContain(KEY);
        assertThat(service.storage().addressHashKey()).isEqualTo(KEY);
        assertThat(this.logged()).noneMatch(line -> line.contains("new address-hash-key"));

        // Without it, a fresh install still gets one.
        final Path other = this.dir.resolve("other");
        Files.createDirectories(other);
        new ConfigService(other, this.log.logger(), Map.of()).load();
        assertThat(Files.readString(other.resolve(ConfigFiles.STORAGE)))
                .doesNotContain(AddressHashKeys.EMPTY_LINE);
        assertThat(this.logged()).anyMatch(line -> line.contains("new address-hash-key"));
    }

    @Test
    void theJsonObjectIsTheBaseAndTheVariablesWin() throws Exception {
        final ConfigService service = this.service(env(
                "SAKURABANS_CONFIG", "{\"server.name\": \"from-json\", \"storage.port\": 3310,"
                        + " \"storage.address-hash-key\": \"" + KEY + "\","
                        + " \"storage\": {\"host\": \"json-host\"}}",
                "SAKURABANS_STORAGE_HOST", "variable-host"));

        service.load();

        assertThat(service.settings().server().name()).isEqualTo("from-json");
        assertThat(service.storage().port()).isEqualTo(3310);
        assertThat(service.storage().host()).isEqualTo("variable-host");
        assertThat(service.storage().addressHashKey()).isEqualTo(KEY);
    }

    @Test
    void valuesAreValidatedLikeTheFilesAndErrorsNameTheVariable() throws Exception {
        // Wrong types first, as with a file: the meaning of the values is checked after.
        assertThatThrownBy(this.service(env(
                "SAKURABANS_STORAGE_PORT", "http",
                "SAKURABANS_STORAGE_TYPE", "oracle",
                "SAKURABANS_SERVER_NAME", "not a name!",
                "SAKURABANS_SERVER_CATEGORIES", "ok,Not OK!"))::load)
                .isInstanceOfSatisfying(ConfigException.class, e -> {
                    assertThat(e.errors()).allSatisfy(issue -> {
                        assertThat(issue.file()).isEqualTo(EnvironmentOverrides.SOURCE);
                        assertThat(issue.message()).endsWith("(from the environment)");
                    });
                    assertThat(e.errors()).extracting(ConfigIssue::path).containsExactlyInAnyOrder(
                            "SAKURABANS_STORAGE_PORT", "SAKURABANS_STORAGE_TYPE",
                            "SAKURABANS_SERVER_NAME", "SAKURABANS_SERVER_CATEGORIES");
                    assertThat(e.errors().stream().filter(issue -> issue.path()
                            .equals("SAKURABANS_STORAGE_PORT")).findFirst().orElseThrow()
                            .message()).contains("\"http\" is not a number");
                });

        assertThatThrownBy(this.service(env(
                "SAKURABANS_STORAGE_ADDRESS_HASH_KEY", "too-short",
                "SAKURABANS_STORAGE_TABLE_PREFIX", "9bad prefix",
                "SAKURABANS_STORAGE_POOL_MAXIMUM_SIZE", "0"))::load)
                .isInstanceOfSatisfying(ConfigException.class, e ->
                        assertThat(e.errors()).extracting(ConfigIssue::path)
                                // minimum-idle (the default 2) is now above the maximum too.
                                .contains("SAKURABANS_STORAGE_ADDRESS_HASH_KEY",
                                        "SAKURABANS_STORAGE_TABLE_PREFIX",
                                        "SAKURABANS_STORAGE_POOL_MAXIMUM_SIZE"));
    }

    @Test
    void secretsAreNeverInAnErrorMessageOrTheLog() throws Exception {
        final String secret = "swordfish-and-more";
        final ConfigService service = this.service(env(
                "SAKURABANS_STORAGE_PASSWORD", secret,
                "SAKURABANS_STORAGE_ADDRESS_HASH_KEY", secret,
                "SAKURABANS_WEB_TOKEN", secret + "-token",
                "SAKURABANS_STORAGE_PORT", "oops"));

        assertThatThrownBy(service::load).isInstanceOfSatisfying(ConfigException.class,
                e -> assertThat(e.getMessage()).doesNotContain(secret));

        final RecordingLogger quiet = new RecordingLogger();
        final ConfigService ok = new ConfigService(this.dir.resolve("ok"), quiet.logger(),
                env("SAKURABANS_STORAGE_PASSWORD", secret,
                        "SAKURABANS_STORAGE_ADDRESS_HASH_KEY", KEY,
                        "SAKURABANS_WEB_TOKEN", secret + "-token"));
        Files.createDirectories(this.dir.resolve("ok"));
        ok.load();
        assertThat(Stream.of("info", "warn", "error").flatMap(level ->
                quiet.entries(level).stream()).map(RecordingLogger.Entry::format))
                .noneMatch(line -> line.contains(secret) || line.contains(KEY))
                .anyMatch(line -> line.contains("SAKURABANS_STORAGE_PASSWORD")
                        && line.contains("SAKURABANS_WEB_TOKEN"));
        assertThat(ok.storage().toString()).doesNotContain(secret, KEY);
        assertThat(ok.settings().toString()).doesNotContain(secret);
    }

    @Test
    void keysThatAreNotFileLevelStopTheStartWithHints() throws Exception {
        final ConfigService service = this.service(env(
                "SAKURABANS_STORAGE_ADDRESS_HASH_KEY", KEY,
                "SAKURABANS_REASONS_DEFAULT", "x"));

        assertThatThrownBy(service::load).isInstanceOfSatisfying(ConfigException.class,
                e -> assertThat(e.errors()).singleElement().satisfies(issue -> {
                    assertThat(issue.path()).isEqualTo("SAKURABANS_REASONS_DEFAULT");
                    assertThat(issue.message()).contains("/sb settings", "web editor");
                }));
    }

    @Test
    void theStartupNoticeNamesTheVariablesAndNeverTheValues() throws Exception {
        this.service(env(
                "SAKURABANS_STORAGE_ADDRESS_HASH_KEY", KEY,
                "SAKURABANS_SERVER_NAME", "visible-name-123",
                "SAKURABANS_CONFIG", "{\"storage.host\": \"db.internal\"}")).load();

        assertThat(this.logged()).anySatisfy(line -> {
            assertThat(line).contains("SAKURABANS_SERVER_NAME",
                    "SAKURABANS_STORAGE_ADDRESS_HASH_KEY", "SAKURABANS_CONFIG[storage.host]");
            assertThat(line).doesNotContain("visible-name-123", KEY, "db.internal");
        });
    }

    @Test
    void withoutAnEnvironmentNothingChangesAndNothingIsLogged() throws Exception {
        final ConfigService service = this.service(Map.of());

        service.load();

        assertThat(service.environment()).isEmpty();
        assertThat(this.logged()).noneMatch(line -> line.contains("environment sets"));
    }

    @Test
    void reloadingKeepsTheEnvironmentApplied() throws Exception {
        this.write(ConfigFiles.CONFIG, "server:\n  name: from-file\n");
        final ConfigService service = this.service(env(
                "SAKURABANS_STORAGE_ADDRESS_HASH_KEY", KEY, "SAKURABANS_SERVER_NAME", "kept"));
        service.load();
        this.write(ConfigFiles.CONFIG, "server:\n  name: edited\n");

        assertThat(service.reload()).isInstanceOf(ReloadResult.Reloaded.class);

        assertThat(service.settings().server().name()).isEqualTo("kept");
    }
}
