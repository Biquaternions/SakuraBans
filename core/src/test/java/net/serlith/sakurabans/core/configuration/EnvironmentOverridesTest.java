package net.serlith.sakurabans.core.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.spongepowered.configurate.ConfigurationNode;

/** S71: which variables exist, how values are read, and what is refused. */
class EnvironmentOverridesTest {

    private static EnvironmentOverrides of(final String... pairs) {
        final Map<String, String> environment = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            environment.put(pairs[i], pairs[i + 1]);
        }
        return EnvironmentOverrides.of(environment);
    }

    private static ConfigurationNode apply(final EnvironmentOverrides overrides,
            final String file) {
        final ConfigurationNode root = ConfigFiles.builder().build().createNode();
        overrides.apply(file, root);
        return root;
    }

    @Test
    void everyFileLevelKeyHasOneVariableAndNoTwoShareOne() {
        final Map<String, String> variables = EnvironmentOverrides.variables();

        assertThat(variables).containsEntry("SAKURABANS_SERVER_NAME", "server.name")
                .containsEntry("SAKURABANS_SERVER_CATEGORIES", "server.categories")
                .containsEntry("SAKURABANS_WEB_TOKEN", "web.token")
                .containsEntry("SAKURABANS_GEOIP_ENABLED", "geoip.enabled")
                .containsEntry("SAKURABANS_GEOIP_ACCOUNT_ID", "geoip.account-id")
                .containsEntry("SAKURABANS_GEOIP_LICENSE_KEY", "geoip.license-key")
                .containsEntry("SAKURABANS_GEOIP_REFRESH", "geoip.refresh")
                .containsEntry("SAKURABANS_STORAGE_TYPE", "storage.type")
                .containsEntry("SAKURABANS_STORAGE_H2_FILE", "storage.h2.file")
                .containsEntry("SAKURABANS_STORAGE_TABLE_PREFIX", "storage.table-prefix")
                .containsEntry("SAKURABANS_STORAGE_PROPERTIES", "storage.properties")
                .containsEntry("SAKURABANS_STORAGE_POOL_MAXIMUM_SIZE",
                        "storage.pool.maximum-size")
                .containsEntry("SAKURABANS_STORAGE_SYNC_POLL_INTERVAL",
                        "storage.sync.poll-interval")
                .containsEntry("SAKURABANS_STORAGE_ADDRESS_HASH_KEY",
                        "storage.address-hash-key");
        assertThat(new HashSet<>(variables.values())).hasSameSizeAs(variables.values());
        // 7 of config.yml (S72 added 4 of geoip), and exactly the keys of the storage model.
        assertThat(variables.keySet().stream().filter(name ->
                name.startsWith("SAKURABANS_STORAGE_"))).hasSize(variables.size() - 7);
        assertThat(variables.values().stream().filter(key -> key.startsWith("storage.")))
                .containsExactlyInAnyOrder("storage.type", "storage.h2.file", "storage.host",
                        "storage.port", "storage.database", "storage.username",
                        "storage.password", "storage.table-prefix", "storage.schema",
                        "storage.properties", "storage.pool.maximum-size",
                        "storage.pool.minimum-idle", "storage.pool.connection-timeout",
                        "storage.pool.max-lifetime", "storage.sync.mode",
                        "storage.sync.poll-interval", "storage.address-hash-key");
        assertThat(EnvironmentOverrides.variableFor(ConfigFiles.STORAGE, "pool.max-lifetime"))
                .contains("SAKURABANS_STORAGE_POOL_MAX_LIFETIME");
    }

    @Test
    void valuesComeFromTheirVariablesAsScalarsListsAndMaps() {
        final EnvironmentOverrides overrides = of(
                "SAKURABANS_SERVER_NAME", "Lobby",
                "SAKURABANS_SERVER_CATEGORIES", " survival , pvp,,",
                "SAKURABANS_STORAGE_POOL_MAXIMUM_SIZE", "20",
                "SAKURABANS_STORAGE_PROPERTIES", "{\"useSSL\": \"true\", \"n\": 5}",
                "PATH", "/usr/bin");

        assertThat(overrides.errors()).isEmpty();
        final ConfigurationNode config = apply(overrides, ConfigFiles.CONFIG);
        assertThat(config.node("server", "name").getString()).isEqualTo("Lobby");
        assertThat(ConfigService.plain(config.node("server", "categories")))
                .isEqualTo(List.of("survival", "pvp"));
        final ConfigurationNode storage = apply(overrides, ConfigFiles.STORAGE);
        assertThat(storage.node("pool", "maximum-size").getString()).isEqualTo("20");
        assertThat(ConfigService.plain(storage.node("properties")))
                .isEqualTo(Map.of("useSSL", "true", "n", "5"));
        // Another file's tree is untouched.
        assertThat(apply(overrides, ConfigFiles.LIMITS).empty()).isTrue();
    }

    @Test
    void listsMayBeJsonArrays() {
        final EnvironmentOverrides overrides =
                of("SAKURABANS_SERVER_CATEGORIES", "[\"a\", \"b,c\"]");

        assertThat(ConfigService.plain(apply(overrides, ConfigFiles.CONFIG)
                .node("server", "categories"))).isEqualTo(List.of("a", "b,c"));
    }

    @Test
    void theJsonObjectTakesDottedOrNestedKeysAndTheVariablesWin() {
        final String json =
                """
                {
                  "server.name": "from-json",
                  "server.categories": ["a", "b"],
                  "storage": {"pool": {"maximum-size": 7, "minimum-idle": 1},
                              "properties": {"x": "y"}},
                  "storage.sync.poll-interval": "500ms",
                  "web.token": "json-token"
                }
                """;
        final EnvironmentOverrides overrides = of("SAKURABANS_CONFIG", json,
                "SAKURABANS_SERVER_NAME", "from-variable");

        assertThat(overrides.errors()).isEmpty();
        final ConfigurationNode config = apply(overrides, ConfigFiles.CONFIG);
        assertThat(config.node("server", "name").getString()).isEqualTo("from-variable");
        assertThat(ConfigService.plain(config.node("server", "categories")))
                .isEqualTo(List.of("a", "b"));
        assertThat(config.node("web", "token").getString()).isEqualTo("json-token");
        final ConfigurationNode storage = apply(overrides, ConfigFiles.STORAGE);
        assertThat(storage.node("pool", "maximum-size").raw()).isEqualTo(7L);
        assertThat(storage.node("sync", "poll-interval").getString()).isEqualTo("500ms");
        assertThat(ConfigService.plain(storage.node("properties")))
                .isEqualTo(Map.of("x", "y"));
        assertThat(overrides.applied()).extracting(EnvironmentOverrides.Applied::source)
                .contains("SAKURABANS_SERVER_NAME",
                        "SAKURABANS_CONFIG[storage.pool.maximum-size]");
    }

    @Test
    void appliedNamesWhatIsSetAndNeverTheValueOfSecrets() {
        final EnvironmentOverrides overrides = of(
                "SAKURABANS_STORAGE_PASSWORD", "hunter2hunter2",
                "SAKURABANS_STORAGE_ADDRESS_HASH_KEY", "k".repeat(40),
                "SAKURABANS_WEB_TOKEN", "tokentokentoken",
                "SAKURABANS_STORAGE_PROPERTIES", "{\"password\": \"p4ssw0rd!\"}",
                "SAKURABANS_STORAGE_HOST", "db.example.org");

        assertThat(overrides.applied()).extracting(EnvironmentOverrides.Applied::key)
                .isSorted();
        assertThat(overrides.applied()).filteredOn(EnvironmentOverrides.Applied::secret)
                .extracting(EnvironmentOverrides.Applied::key)
                .containsExactlyInAnyOrder("storage.password", "storage.address-hash-key",
                        "web.token", "storage.properties")
                .allSatisfy(key -> assertThat(overrides.applied().stream()
                        .filter(applied -> applied.key().equals(key)).findFirst().orElseThrow()
                        .value()).isNull());
        assertThat(overrides.applied()).filteredOn(applied -> applied.key().equals("storage.host"))
                .singleElement().satisfies(applied ->
                        assertThat(applied.value()).isEqualTo("db.example.org"));
        assertThat(overrides.applied().toString()).doesNotContain("hunter2hunter2", "tokentoken",
                "p4ssw0rd");
        assertThat(overrides.supplies(ConfigFiles.STORAGE, "address-hash-key")).isTrue();
        assertThat(overrides.supplies(ConfigFiles.STORAGE, "port")).isFalse();
    }

    @Test
    void keysThatAreNotFileLevelAreRefusedWithHintsWhereTheyLive() {
        final EnvironmentOverrides overrides = of(
                "SAKURABANS_REASONS_DEFAULT", "x",
                "SAKURABANS_ENFORCEMENT_ADDRESS_STRICTNESS", "STERN",
                "SAKURABANS_COMMANDS_LABELS", "{}",
                "SAKURABANS_WEB_URL", "https://example.org",
                "SAKURABANS_LIMITS_GROUPS", "{}",
                "SAKURABANS_NOT_A_SETTING", "x");

        assertThat(overrides.errors()).allSatisfy(issue -> {
            assertThat(issue.file()).isEqualTo(EnvironmentOverrides.SOURCE);
            assertThat(issue.severity()).isEqualTo(ConfigIssue.Severity.ERROR);
        });
        assertThat(overrides.errors()).extracting(ConfigIssue::path).containsExactly(
                "SAKURABANS_COMMANDS_LABELS", "SAKURABANS_ENFORCEMENT_ADDRESS_STRICTNESS",
                "SAKURABANS_LIMITS_GROUPS", "SAKURABANS_NOT_A_SETTING",
                "SAKURABANS_REASONS_DEFAULT", "SAKURABANS_WEB_URL");
        final Map<String, String> byVariable = new HashMap<>();
        overrides.errors().forEach(issue -> byVariable.put(issue.path(), issue.message()));
        assertThat(byVariable.get("SAKURABANS_REASONS_DEFAULT"))
                .contains("reasons.default", "/sb settings", "web editor");
        assertThat(byVariable.get("SAKURABANS_ENFORCEMENT_ADDRESS_STRICTNESS"))
                .contains("global setting", "/sb settings");
        assertThat(byVariable.get("SAKURABANS_LIMITS_GROUPS")).contains("scoped", "/sb settings");
        assertThat(byVariable.get("SAKURABANS_COMMANDS_LABELS"))
                .contains("can only be set in config.yml");
        assertThat(byVariable.get("SAKURABANS_WEB_URL")).contains("config.yml");
        assertThat(byVariable.get("SAKURABANS_NOT_A_SETTING"))
                .contains("isn't a setting the environment can set", "server.name");
        assertThat(overrides.empty()).isTrue();
    }

    @Test
    void theJsonObjectIsCheckedTheSameWay() {
        final String json =
                """
                {"reasons.default": "x", "enforcement": {"address-strictness": "STERN"},
                 "storage.pool.maximum-size": [1], "storage.properties": 3,
                 "server.name": null, "web": {"nope": 1}, "server.categories": "a, b"}
                """;
        final EnvironmentOverrides overrides = of("SAKURABANS_CONFIG", json);

        assertThat(overrides.errors()).extracting(ConfigIssue::path).containsExactlyInAnyOrder(
                "SAKURABANS_CONFIG[reasons.default]",
                "SAKURABANS_CONFIG[enforcement.address-strictness]",
                "SAKURABANS_CONFIG[storage.pool.maximum-size]",
                "SAKURABANS_CONFIG[storage.properties]", "SAKURABANS_CONFIG[server.name]",
                "SAKURABANS_CONFIG[web.nope]");
        assertThat(overrides.errors()).filteredOn(issue ->
                issue.path().equals("SAKURABANS_CONFIG[reasons.default]")).singleElement()
                .satisfies(issue -> assertThat(issue.message()).contains("/sb settings"));
        // A string for a list is split like the variable.
        assertThat(overrides.applied()).extracting(EnvironmentOverrides.Applied::key)
                .containsExactly("server.categories");
    }

    @Test
    void brokenJsonAndWrongShapesNameTheirVariableWithoutEchoingTheText() {
        for (final String broken : List.of("{\"server.name\": \"secret-text", "[1, 2]", "{")) {
            final EnvironmentOverrides overrides = of("SAKURABANS_CONFIG", broken);

            assertThat(overrides.errors()).singleElement().satisfies(issue -> {
                assertThat(issue.path()).isEqualTo("SAKURABANS_CONFIG");
                assertThat(issue.message()).doesNotContain("secret-text");
            });
        }
        final EnvironmentOverrides map = of("SAKURABANS_STORAGE_PROPERTIES", "[1]",
                "SAKURABANS_STORAGE_PASSWORD", "x");
        assertThat(map.errors()).singleElement().satisfies(issue -> {
            assertThat(issue.path()).isEqualTo("SAKURABANS_STORAGE_PROPERTIES");
            assertThat(issue.message()).contains("JSON object");
        });
        assertThat(of("SAKURABANS_STORAGE_PROPERTIES", "{\"a\": [1]}").errors())
                .singleElement().satisfies(issue -> assertThat(issue.message())
                        .contains("single values"));
        assertThat(of("SAKURABANS_SERVER_CATEGORIES", "[[1]]").errors()).singleElement();
    }

    @Test
    void anIssueAboutAnEnvironmentValueIsNamedByItsVariable() {
        final EnvironmentOverrides overrides = of(
                "SAKURABANS_STORAGE_POOL_MAXIMUM_SIZE", "abc",
                "SAKURABANS_STORAGE_PASSWORD", "swordfish99");

        assertThat(overrides.label(ConfigIssue.error(ConfigFiles.STORAGE, "pool.maximum-size",
                "\"abc\" is not a number")))
                .isEqualTo(new ConfigIssue(EnvironmentOverrides.SOURCE,
                        "SAKURABANS_STORAGE_POOL_MAXIMUM_SIZE",
                        "\"abc\" is not a number (from the environment)",
                        ConfigIssue.Severity.ERROR));
        // A secret's text never stays in a message.
        assertThat(overrides.label(ConfigIssue.error(ConfigFiles.STORAGE, "password",
                "\"swordfish99\" is wrong")).message()).doesNotContain("swordfish99")
                .contains("<hidden>");
        // Other keys, other files: as they are.
        final ConfigIssue other = ConfigIssue.error(ConfigFiles.STORAGE, "port", "bad");
        assertThat(overrides.label(other)).isSameAs(other);
        final ConfigIssue otherFile = ConfigIssue.error(ConfigFiles.CONFIG, "pool.maximum-size",
                "bad");
        assertThat(overrides.label(otherFile)).isSameAs(otherFile);
    }

    @Test
    void anEmptyEnvironmentSetsNothing() {
        assertThat(EnvironmentOverrides.NONE.empty()).isTrue();
        assertThat(of().errors()).isEmpty();
        assertThat(of("HOME", "/root", "SAKURABANSX", "1").applied()).isEmpty();
    }
}
