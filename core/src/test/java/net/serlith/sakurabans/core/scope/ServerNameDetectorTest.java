package net.serlith.sakurabans.core.scope;

import java.nio.file.Files;
import java.nio.file.Path;
import net.serlith.sakurabans.core.RecordingLogger;
import net.serlith.sakurabans.core.TestConfig;
import net.serlith.sakurabans.core.configuration.ConfigService;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ServerNameDetectorTest {

    @TempDir
    Path dir;

    private final RecordingLogger log = new RecordingLogger();

    private ServerNameDetector detector(final String yaml) {
        final ConfigService config = TestConfig.load(this.dir, yaml, this.log);
        return new ServerNameDetector(this.dir, config, Runnable::run, this.log.logger());
    }

    @Test
    void theFirstNameOfTheRunIsTaken() {
        final ServerNameDetector detector = this.detector("");
        Assertions.assertThat(detector.wanted()).isTrue();

        Assertions.assertThat(detector.detected("Survival")).isTrue();
        Assertions.assertThat(detector.name()).contains("survival");
        Assertions.assertThat(detector.wanted()).isFalse();
        // The proxy sends it on every switch; a rename counts from the next start.
        Assertions.assertThat(detector.detected("other")).isFalse();
        Assertions.assertThat(detector.name()).contains("survival");
    }

    @Test
    void theNameIsStoredForTheNextStart() {
        this.detector("").detected("survival");
        Assertions.assertThat(this.dir.resolve(ServerNameDetector.FILE_NAME))
                .content().isEqualToIgnoringWhitespace("survival");

        final ServerNameDetector next = this.detector("");
        next.load();
        Assertions.assertThat(next.name()).contains("survival");
        Assertions.assertThat(next.wanted()).isTrue();
    }

    @Test
    void invalidNamesAreRejected() throws Exception {
        Files.writeString(this.dir.resolve(ServerNameDetector.FILE_NAME), "not valid!\n");
        final ServerNameDetector detector = this.detector("");
        detector.load();
        Assertions.assertThat(detector.name()).isEmpty();

        Assertions.assertThat(detector.detected("my server")).isFalse();
        Assertions.assertThat(detector.name()).isEmpty();
    }

    @Test
    void configuredNamesNeedNoDetection() {
        final ServerNameDetector detector = this.detector("server:\n  name: lobby\n");
        Assertions.assertThat(detector.wanted()).isFalse();
        Assertions.assertThat(detector.detected("survival")).isFalse();
    }
}
