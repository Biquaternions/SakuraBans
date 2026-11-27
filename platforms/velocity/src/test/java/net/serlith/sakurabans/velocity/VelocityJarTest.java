package net.serlith.sakurabans.velocity;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

/** The shaded jar a proxy owner installs; the build passes its path. */
class VelocityJarTest {

    private static Path jar() {
        final String path = System.getProperty("sakurabans.shadowJar");
        Assertions.assertThat(path).as("sakurabans.shadowJar").isNotNull();
        return Path.of(path);
    }

    private static List<String> entries() throws IOException {
        try (ZipFile jar = new ZipFile(jar().toFile())) {
            return jar.stream().map(ZipEntry::getName).toList();
        }
    }

    @Test
    void geoIpsReaderIsShadedAndRelocated() throws IOException {
        final List<String> entries = entries();

        Assertions.assertThat(entries)
                .contains("net/serlith/sakurabans/libs/maxmind/db/Reader.class",
                        "net/serlith/sakurabans/core/geoip/MaxMindCountryDatabase.class")
                .noneMatch(name -> name.startsWith("com/maxmind/"));
    }

    @Test
    void theRelocatedReaderReadsTheTestDatabase() throws Exception {
        // MaxMind's test database (core test resources; geoip/README there).
        final Path database = Path.of("../../core/src/test/resources/geoip/"
                + "GeoLite2-Country-Test.mmdb");
        Assertions.assertThat(database).exists();
        final Path copy = Files.createTempFile("GeoLite2-Country", ".mmdb");
        Files.copy(database, copy, StandardCopyOption.REPLACE_EXISTING);
        // Only the jar and the JDK: what the proxy would give the plugin's classes.
        try (URLClassLoader loader = new URLClassLoader(new URL[] {jar().toUri().toURL()},
                ClassLoader.getPlatformClassLoader())) {
            final Class<?> type = loader.loadClass(
                    "net.serlith.sakurabans.core.geoip.MaxMindCountryDatabase");
            final Object opened = type.getMethod("open", Path.class).invoke(null, copy);
            final Object country = loader.loadClass(
                            "net.serlith.sakurabans.core.geoip.CountryDatabase")
                    .getMethod("country", InetAddress.class)
                    .invoke(opened, InetAddress.ofLiteral("81.2.69.142"));

            Assertions.assertThat(country).hasToString(
                    "Optional[Country[code=GB, name=United Kingdom]]");
        } finally {
            Files.deleteIfExists(copy);
        }
    }
}
