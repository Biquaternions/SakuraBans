package net.serlith.sakurabans.core.command.module;

import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import net.serlith.sakurabans.core.RecordingLogger;
import net.serlith.sakurabans.core.storage.StorageService;
import net.serlith.sakurabans.core.storage.dbo.SakuraTables;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

/** The address hash key is never stored, exported, uploaded or logged. */
class AddressHashKeySecrecyTest extends EditorTest {

    @Test
    void theKeyIsInNoExportPayloadRegistryRowMetaValueOrLogLine() throws Exception {
        final String key = this.config.storage().addressHashKey();
        Assertions.assertThat(key).hasSizeGreaterThanOrEqualTo(32);
        this.known(STEVE, "Steve");
        this.players.recordConnection(STEVE, "Steve", this.now.get(),
                InetAddress.getByName("203.0.113.7"), null).join();
        this.run(this.console, "ban 203.0.113.7 -r spam");
        Assertions.assertThat(this.count(SakuraTables.ACTIVE_PUNISHMENTS)).isOne();

        this.run(this.console, "sb gdpr export Steve");
        final List<Path> exports;
        try (Stream<Path> listing = Files.list(this.dir.resolve("exports"))) {
            exports = listing.toList();
        }
        Assertions.assertThat(exports).singleElement().satisfies(file ->
                Assertions.assertThat(Files.readString(file)).doesNotContain(key));

        Assertions.assertThat(this.open(this.console, "").payload().toString())
                .doesNotContain(key).doesNotContain("address-hash-key");
        Assertions.assertThat(this.registry.self().fileValues()).doesNotContain(key)
                .doesNotContain("address-hash-key");

        final List<String> meta = this.read(dsl -> dsl.select(SakuraTables.META.V)
                .from(SakuraTables.META).fetch(SakuraTables.META.V));
        Assertions.assertThat(meta).noneMatch(value -> value.contains(key))
                .contains(this.hasher.fingerprint());
        Assertions.assertThat(this.count(SakuraTables.META,
                SakuraTables.META.K.eq(StorageService.ADDRESS_HASH_KEY_CHECK))).isOne();

        Assertions.assertThat(Stream.of("trace", "debug", "info", "warn", "error")
                .flatMap(level -> this.log.entries(level).stream())
                .map(RecordingLogger.Entry::format)).noneMatch(line -> line.contains(key));
        Assertions.assertThat(this.config.storage().toString()).doesNotContain(key);
    }
}
