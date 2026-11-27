package net.serlith.sakurabans.core.command.module;

import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import net.serlith.sakurabans.core.RecordingLogger;
import net.serlith.sakurabans.core.storage.dbo.SakuraTables;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * S71: what the environment sets is listed by name, and its secrets are in no payload, registry
 * row, meta value or log line.
 */
class EnvironmentCommandsTest extends EditorTest {

    private static final String KEY = "environment-address-hash-key-0123456789";
    private static final String PASSWORD = "pw-secret-from-the-environment";

    @Override
    protected String configYaml() {
        // The token isn't in the file: the environment gives it.
        return "web:\n  url: '" + this.site.url() + "/'\n";
    }

    @Override
    protected Map<String, String> environment() {
        return Map.of(
                "SAKURABANS_WEB_TOKEN", TOKEN,
                "SAKURABANS_SERVER_CATEGORIES", "survival",
                "SAKURABANS_STORAGE_PASSWORD", PASSWORD,
                "SAKURABANS_STORAGE_ADDRESS_HASH_KEY", KEY);
    }

    private List<String> lines(final String command) {
        this.console.clear();
        this.run(this.console, command);
        return this.console.texts();
    }

    @Test
    void effectiveListsTheEnvironmentByNameWithoutSecrets() {
        final List<String> lines = this.lines("sb settings list --effective");

        Assertions.assertThat(lines).contains(
                "SakuraBans ...🖉 Set by the environment (4; file-level settings, they win over"
                        + " config.yml and storage.yml)",
                " - SAKURABANS_SERVER_CATEGORIES sets server.categories: [survival]",
                " - SAKURABANS_STORAGE_ADDRESS_HASH_KEY sets storage.address-hash-key: (set,"
                        + " hidden)",
                " - SAKURABANS_STORAGE_PASSWORD sets storage.password: (set, hidden)",
                " - SAKURABANS_WEB_TOKEN sets web.token: (set, hidden)");
        Assertions.assertThat(String.join("\n", lines))
                .doesNotContain(KEY, PASSWORD, TOKEN);
        // The environment decided the server's categories, so the scopes follow.
        Assertions.assertThat(this.config.settings().server().categories())
                .containsExactly("survival");
        Assertions.assertThat(lines.getFirst()).contains("category:survival");
    }

    @Test
    void theEnvironmentsSecretsAreInNoPayloadRegistryRowMetaValueOrLog() throws Exception {
        this.known(STEVE, "Steve");
        this.registry.refresh().join();

        Assertions.assertThat(this.open(this.console, "").payload().toString())
                .doesNotContain(KEY, PASSWORD, TOKEN, "address-hash-key");
        Assertions.assertThat(this.registry.self().fileValues())
                .doesNotContain(KEY, PASSWORD, TOKEN);
        Assertions.assertThat(this.registry.self().toString())
                .doesNotContain(KEY, PASSWORD, TOKEN);
        final List<String> meta = this.read(dsl -> dsl.select(SakuraTables.META.V)
                .from(SakuraTables.META).fetch(SakuraTables.META.V));
        Assertions.assertThat(meta).noneMatch(value -> value.contains(KEY)
                || value.contains(PASSWORD)).contains(this.hasher.fingerprint());
        Assertions.assertThat(Stream.of("trace", "debug", "info", "warn", "error")
                .flatMap(level -> this.log.entries(level).stream())
                .map(RecordingLogger.Entry::format))
                .noneMatch(line -> line.contains(KEY) || line.contains(PASSWORD)
                        || line.contains(TOKEN))
                .anyMatch(line -> line.contains("SAKURABANS_STORAGE_PASSWORD"));
        Assertions.assertThat(this.config.storage().toString()).doesNotContain(KEY, PASSWORD);
        Assertions.assertThat(this.config.settings().toString()).doesNotContain(TOKEN);

        // The fresh storage.yml has no key: the environment's is the only one.
        Assertions.assertThat(Files.readString(this.dir.resolve("storage.yml")))
                .doesNotContain(KEY, PASSWORD).contains("address-hash-key: ''");
        Assertions.assertThat(this.hasher.fingerprint()).isNotEmpty();
    }
}
