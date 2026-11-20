package net.serlith.sakurabans.core;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.serlith.sakurabans.core.configuration.ConfigException;
import net.serlith.sakurabans.core.configuration.ConfigService;

public final class TestConfig {

    private TestConfig() {
    }

    public static ConfigService load(final Path dir, final String yaml,
            final RecordingLogger log) {
        try {
            final Path file = dir.resolve("config.yml");
            if (yaml.isBlank()) {
                Files.deleteIfExists(file);
            } else {
                Files.writeString(file, yaml);
            }
            final ConfigService config = new ConfigService(dir, log.logger());
            config.load();
            return config;
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        } catch (final ConfigException e) {
            throw new IllegalStateException(e);
        }
    }

}
