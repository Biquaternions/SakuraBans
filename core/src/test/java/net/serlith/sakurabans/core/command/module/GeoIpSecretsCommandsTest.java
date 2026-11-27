package net.serlith.sakurabans.core.command.module;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import net.serlith.sakurabans.core.RecordingLogger;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * S72: the MaxMind account, from the file or the environment, is in no editor payload,
 * registry row, settings list or log line; the global country lists are in the payload.
 */
class GeoIpSecretsCommandsTest extends EditorTest {

    private static final String ACCOUNT = "915577";
    private static final String KEY = "File_Licence-Key-0123456789";
    private static final String ENVIRONMENT_KEY = "Environment_Licence-Key-987";

    @Override
    protected String configYaml() {
        return super.configYaml() + "geoip:\n  enabled: false\n  account-id: '" + ACCOUNT
                + "'\n  license-key: " + KEY + "\n  blacklist: [cn]\n";
    }

    @Override
    protected Map<String, String> environment() {
        return Map.of("SAKURABANS_GEOIP_LICENSE_KEY", ENVIRONMENT_KEY);
    }

    @Test
    void theAccountIsInNoPayloadRowListOrLog() {
        this.registry.refresh().join();
        Assertions.assertThat(this.config.settings().geoip().licenseKey())
                .isEqualTo(ENVIRONMENT_KEY);

        final String payload = this.open(this.console, "").payload().toString();
        Assertions.assertThat(payload).doesNotContain(ACCOUNT, KEY, ENVIRONMENT_KEY,
                "geoip.account-id", "geoip.license-key").contains("geoip.blacklist");
        Assertions.assertThat(this.registry.self().fileValues())
                .doesNotContain(ACCOUNT, KEY, ENVIRONMENT_KEY).contains("geoip.blacklist", "cn");
        this.console.clear();
        this.run(this.console, "sb settings list --effective");
        Assertions.assertThat(String.join("\n", this.console.texts()))
                .doesNotContain(ACCOUNT, KEY, ENVIRONMENT_KEY)
                .contains("SAKURABANS_GEOIP_LICENSE_KEY sets geoip.license-key: (set, hidden)");
        Assertions.assertThat(Stream.of("trace", "debug", "info", "warn", "error")
                .flatMap(level -> this.log.entries(level).stream())
                .map(RecordingLogger.Entry::format))
                .noneMatch(line -> line.contains(ACCOUNT) || line.contains(KEY)
                        || line.contains(ENVIRONMENT_KEY));
        Assertions.assertThat(List.of(this.config.settings().toString()))
                .noneMatch(text -> text.contains(ACCOUNT) || text.contains(ENVIRONMENT_KEY));
    }
}
