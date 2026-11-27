package net.serlith.sakurabans.core;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import net.serlith.sakurabans.core.configuration.ConfigException;
import net.serlith.sakurabans.core.configuration.ConfigService;

public final class TestConfig {

    private TestConfig() {
    }

    public static ConfigService load(final Path dir, final String yaml,
            final RecordingLogger log) {
        return load(dir, yaml, log, Map.of());
    }

    public static ConfigService load(final Path dir, final String yaml,
            final RecordingLogger log, final Map<String, String> environment) {
        try {
            final Path file = dir.resolve("config.yml");
            if (yaml.isBlank()) {
                Files.deleteIfExists(file);
            } else {
                Files.writeString(file, yaml);
            }
            // The shipped console cooldown would refuse the tests' many punishments of one player
            // at one fixed instant; the tests of the cooldown write their own limits.yml.
            final Path limits = dir.resolve("limits.yml");
            if (Files.notExists(limits)) {
                Files.writeString(limits,
                        "reduce-to-limit: true\nconsole-cooldown: 0s\ngroups: {}\n");
            }
            final ConfigService config = new ConfigService(dir, log.logger(), environment);
            config.load();
            return config;
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        } catch (final ConfigException e) {
            throw new IllegalStateException(e);
        }
    }

}
