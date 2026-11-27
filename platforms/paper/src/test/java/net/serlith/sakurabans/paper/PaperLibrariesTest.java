package net.serlith.sakurabans.paper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

/** The generated library list Paper resolves at load (plan 3.3). */
class PaperLibrariesTest {

    private static List<String> libraries() throws IOException {
        try (InputStream input = PaperLibrariesTest.class.getResourceAsStream(
                "/paper-libraries.txt")) {
            Assertions.assertThat(input).as("paper-libraries.txt").isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8).lines()
                    .map(String::strip).filter(line -> !line.isEmpty()).toList();
        }
    }

    @Test
    void geoIpsReaderIsResolvedByPaper() throws IOException {
        // S72: eager, like the other libraries; only loaded where geoip.enabled is true.
        Assertions.assertThat(libraries()).contains("com.maxmind.db:maxmind-db:4.2.0");
    }

    @Test
    void theShadedLibrariesAreNotListed() throws IOException {
        Assertions.assertThat(libraries()).noneMatch(line -> line.startsWith("com.google.guava:")
                || line.startsWith("com.google.code.gson:"));
    }
}
