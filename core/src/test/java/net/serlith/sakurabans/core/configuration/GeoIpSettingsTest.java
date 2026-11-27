package net.serlith.sakurabans.core.configuration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import net.serlith.sakurabans.core.RecordingLogger;
import net.serlith.sakurabans.core.configuration.settings.Settings;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** S72: the {@code geoip} section, its checks and its environment variables. */
class GeoIpSettingsTest {

    private static final String ACCOUNT = "761234";
    private static final String KEY = "Lic3nse_Key-from-maxmind";

    @TempDir
    private Path dir;

    private final RecordingLogger log = new RecordingLogger();

    private ConfigService load(final String yaml) throws Exception {
        return this.load(yaml, Map.of());
    }

    private ConfigService load(final String yaml, final Map<String, String> environment)
            throws Exception {
        Files.writeString(this.dir.resolve(ConfigFiles.CONFIG), yaml);
        final ConfigService service = new ConfigService(this.dir, this.log.logger(),
                environment);
        service.load();
        return service;
    }

    private List<ConfigIssue> errors(final String yaml) {
        return this.errors(yaml, Map.of());
    }

    private List<ConfigIssue> errors(final String yaml, final Map<String, String> environment) {
        final Throwable thrown = Assertions.catchThrowable(() -> this.load(yaml, environment));
        Assertions.assertThat(thrown).isInstanceOf(ConfigException.class);
        return ((ConfigException) thrown).errors();
    }

    private List<String> logged() {
        return Stream.of("trace", "debug", "info", "warn", "error")
                .flatMap(level -> this.log.entries(level).stream())
                .map(RecordingLogger.Entry::format).toList();
    }

    @Test
    void theDefaultsAreOffWithoutAccountAndWithoutLists() throws Exception {
        final Settings.GeoIp geoip = this.load("").settings().geoip();

        Assertions.assertThat(geoip.enabled()).isFalse();
        Assertions.assertThat(geoip.accountId()).isNull();
        Assertions.assertThat(geoip.licenseKey()).isNull();
        Assertions.assertThat(geoip.downloads()).isFalse();
        Assertions.assertThat(geoip.refresh()).isEqualTo(Duration.ofDays(7));
        Assertions.assertThat(geoip.blacklist()).isEmpty();
        Assertions.assertThat(geoip.whitelist()).isEmpty();
        Assertions.assertThat(geoip.allowUnknown()).isTrue();
    }

    @Test
    void theShippedFileHasTheSameDefaults() throws Exception {
        final ConfigService shipped = new ConfigService(this.dir, this.log.logger());
        shipped.load();
        final String text = Files.readString(this.dir.resolve(ConfigFiles.CONFIG));

        Assertions.assertThat(shipped.settings().geoip())
                .isEqualTo(this.load("").settings().geoip());
        Assertions.assertThat(text)
                .contains("geoip:\n  #", "  enabled: false\n", "  account-id: ''\n",
                        "  license-key: ''\n", "  refresh: 7d\n", "  blacklist: []\n",
                        "  whitelist: []\n", "  allow-unknown: true\n");
    }

    @Test
    void codesAreReadInAnyCaseAndKeptInUpperCase() throws Exception {
        final Settings.GeoIp geoip = this.load("geoip:\n  enabled: true\n  account-id: "
                + ACCOUNT + "\n  license-key: " + KEY + "\n  refresh: never\n"
                + "  blacklist: [de, ' us ', Cn]\n  allow-unknown: false\n").settings().geoip();

        Assertions.assertThat(geoip.enabled()).isTrue();
        Assertions.assertThat(geoip.accountId()).isEqualTo(ACCOUNT);
        Assertions.assertThat(geoip.licenseKey()).isEqualTo(KEY);
        Assertions.assertThat(geoip.downloads()).isTrue();
        Assertions.assertThat(geoip.refresh()).isNull();
        Assertions.assertThat(geoip.blacklist()).containsExactlyInAnyOrder("DE", "US", "CN");
        Assertions.assertThat(geoip.allowUnknown()).isFalse();
        Assertions.assertThat(geoip.toString()).doesNotContain(ACCOUNT, KEY);
    }

    @Test
    void unknownCodesAreErrorsWithTheirKeyPath() {
        Assertions.assertThat(this.errors(
                        "geoip:\n  blacklist: [DE, UK]\n  whitelist: [XX, Germany]\n"))
                .extracting(ConfigIssue::path, ConfigIssue::message)
                .containsExactly(
                        Assertions.tuple("geoip.blacklist", "\"UK\" is not an ISO 3166-1"
                                + " alpha-2 country code (like US, DE or GB)"),
                        Assertions.tuple("geoip.whitelist", "\"XX\" is not an ISO 3166-1"
                                + " alpha-2 country code (like US, DE or GB)"),
                        Assertions.tuple("geoip.whitelist", "\"Germany\" is not an ISO 3166-1"
                                + " alpha-2 country code (like US, DE or GB)"));
    }

    @Test
    void bothListsLoadWithWarning() throws Exception {
        final Settings.GeoIp geoip = this.load("geoip:\n  blacklist: [RU]\n"
                + "  whitelist: [DE, AT]\n").settings().geoip();

        Assertions.assertThat(geoip.whitelist()).containsExactlyInAnyOrder("DE", "AT");
        Assertions.assertThat(this.log.entries("warn")).extracting(RecordingLogger.Entry::format)
                .contains("Configuration: config.yml: geoip.blacklist: is ignored while"
                        + " geoip.whitelist is set: only the whitelisted countries can join");
    }

    @Test
    void theRefreshIsAtLeastOneDayOrNever() throws Exception {
        Assertions.assertThat(this.errors("geoip:\n  refresh: 12h\n"))
                .extracting(ConfigIssue::path).containsExactly("geoip.refresh");
        Assertions.assertThat(this.errors("geoip:\n  refresh: soon\n"))
                .extracting(ConfigIssue::path).containsExactly("geoip.refresh");
        Assertions.assertThat(this.load("geoip:\n  refresh: 1d\n").settings().geoip().refresh())
                .isEqualTo(Duration.ofDays(1));
    }

    @Test
    void theAccountNeedsBothPartsAndNoErrorRepeatsThem() {
        Assertions.assertThat(this.errors("geoip:\n  account-id: '" + ACCOUNT + "'\n"))
                .extracting(ConfigIssue::path).containsExactly("geoip.license-key");
        Assertions.assertThat(this.errors("geoip:\n  license-key: " + KEY + "\n"))
                .extracting(ConfigIssue::path).containsExactly("geoip.account-id");

        final List<ConfigIssue> wrong = this.errors("geoip:\n  account-id: 'acct-" + ACCOUNT
                + "'\n  license-key: '" + KEY + " !'\n");
        Assertions.assertThat(wrong).extracting(ConfigIssue::path)
                .containsExactly("geoip.account-id", "geoip.license-key");
        Assertions.assertThat(wrong.toString()).doesNotContain(ACCOUNT, KEY);
    }

    @Test
    void theEnvironmentSetsTheFileLevelKeys() throws Exception {
        final ConfigService service = this.load("geoip:\n  license-key: file-key\n", Map.of(
                "SAKURABANS_GEOIP_ENABLED", "true",
                "SAKURABANS_GEOIP_ACCOUNT_ID", ACCOUNT,
                "SAKURABANS_GEOIP_LICENSE_KEY", KEY,
                "SAKURABANS_GEOIP_REFRESH", "2d"));
        final Settings.GeoIp geoip = service.settings().geoip();

        Assertions.assertThat(geoip.enabled()).isTrue();
        Assertions.assertThat(geoip.accountId()).isEqualTo(ACCOUNT);
        Assertions.assertThat(geoip.licenseKey()).isEqualTo(KEY);
        Assertions.assertThat(geoip.refresh()).isEqualTo(Duration.ofDays(2));
        Assertions.assertThat(service.environment())
                .extracting(EnvironmentOverrides.Applied::source,
                        EnvironmentOverrides.Applied::secret, EnvironmentOverrides.Applied::value)
                .containsExactly(
                        Assertions.tuple("SAKURABANS_GEOIP_ACCOUNT_ID", true, null),
                        Assertions.tuple("SAKURABANS_GEOIP_ENABLED", false, "true"),
                        Assertions.tuple("SAKURABANS_GEOIP_LICENSE_KEY", true, null),
                        Assertions.tuple("SAKURABANS_GEOIP_REFRESH", false, "2d"));
        Assertions.assertThat(this.logged()).noneMatch(line -> line.contains(ACCOUNT)
                || line.contains(KEY));
        Assertions.assertThat(Files.readString(this.dir.resolve(ConfigFiles.CONFIG)))
                .doesNotContain(KEY);
    }

    @Test
    void theEnvironmentsBadValuesAreNamedByTheirVariableWithoutTheSecret() {
        final List<ConfigIssue> errors = this.errors("", Map.of(
                "SAKURABANS_GEOIP_ACCOUNT_ID", ACCOUNT,
                "SAKURABANS_GEOIP_LICENSE_KEY", KEY + " with spaces",
                "SAKURABANS_GEOIP_ENABLED", "maybe"));

        Assertions.assertThat(errors).extracting(ConfigIssue::path)
                .containsExactly("SAKURABANS_GEOIP_ENABLED");
        final List<ConfigIssue> key = this.errors("", Map.of(
                "SAKURABANS_GEOIP_ACCOUNT_ID", ACCOUNT,
                "SAKURABANS_GEOIP_LICENSE_KEY", KEY + " with spaces"));
        Assertions.assertThat(key).extracting(ConfigIssue::file, ConfigIssue::path)
                .containsExactly(Assertions.tuple(EnvironmentOverrides.SOURCE,
                        "SAKURABANS_GEOIP_LICENSE_KEY"));
        Assertions.assertThat(key.toString()).doesNotContain(ACCOUNT, KEY);
    }

    @Test
    void theGlobalKeysAreRefusedFromTheEnvironmentWithHint() {
        final List<ConfigIssue> errors = this.errors("", Map.of(
                "SAKURABANS_GEOIP_BLACKLIST", "CN"));

        Assertions.assertThat(errors).singleElement().extracting(ConfigIssue::message)
                .asString().contains("geoip.blacklist is a global setting",
                        "/sb settings");
    }

    @Test
    void theAccountIsSecretAndOnlyInTheFile() {
        for (final String key : List.of("geoip.account-id", "geoip.license-key")) {
            Assertions.assertThat(SettingsSchema.secret(key)).as(key).isTrue();
            Assertions.assertThat(SettingsSchema.entry(key).orElseThrow().editable()).as(key)
                    .isFalse();
        }
        Assertions.assertThat(SettingsSchema.entry("geoip.enabled").orElseThrow().level())
                .isEqualTo(SettingsSchema.Level.FILE);
        Assertions.assertThat(SettingsSchema.entry("geoip.refresh").orElseThrow().level())
                .isEqualTo(SettingsSchema.Level.FILE);
        for (final String key : List.of("geoip.blacklist", "geoip.whitelist",
                "geoip.allow-unknown")) {
            Assertions.assertThat(SettingsSchema.entry(key).orElseThrow().level()).as(key)
                    .isEqualTo(SettingsSchema.Level.GLOBAL);
        }
    }

    @Test
    void turningGeoIpOnOrOffNeedsRestart() throws Exception {
        final ConfigService service = this.load("");
        Files.writeString(this.dir.resolve(ConfigFiles.CONFIG),
                "geoip:\n  enabled: true\n  refresh: 3d\n");

        final ReloadResult result = service.reload();

        Assertions.assertThat(result).isInstanceOfSatisfying(ReloadResult.Reloaded.class,
                reloaded -> Assertions.assertThat(reloaded.restartRequired())
                        .containsExactly("geoip.enabled"));
        // The refresh is read per use, so it applies at once.
        Assertions.assertThat(service.settings().geoip().refresh())
                .isEqualTo(Duration.ofDays(3));
    }
}
