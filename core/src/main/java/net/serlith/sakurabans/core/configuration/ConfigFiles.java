package net.serlith.sakurabans.core.configuration;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.SecureRandom;
import java.time.Duration;
import net.serlith.sakurabans.core.configuration.serializer.DurationSerializer;
import org.spongepowered.configurate.yaml.NodeStyle;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

final class ConfigFiles {

    static final String CONFIG = "config.yml";
    static final String STORAGE = "storage.yml";
    static final String LIMITS = "limits.yml";
    static final String WEBHOOKS = "webhooks.yml";
    static final String IMPORT = "import.yml";

    private ConfigFiles() {
    }

    static YamlConfigurationLoader loader(final Path file) {
        return builder().path(file).build();
    }

    static YamlConfigurationLoader.Builder builder() {
        return YamlConfigurationLoader.builder()
                .nodeStyle(NodeStyle.BLOCK)
                .indent(2)
                .defaultOptions(options -> options.serializers(builder ->
                        builder.register(Duration.class, new DurationSerializer())));
    }

    static Path ensureDefault(final Path directory, final String name) throws IOException {
        return ensureDefault(directory, name, true);
    }

    /**
     * Copies the shipped file if there is none. {@code generateKey} false leaves the
     * {@code address-hash-key} of a fresh {@code storage.yml} empty, because the environment
     * supplies it.
     */
    static Path ensureDefault(final Path directory, final String name,
            final boolean generateKey) throws IOException {
        final Path file = directory.resolve(name);
        if (Files.notExists(file)) {
            Files.createDirectories(directory);
            try (InputStream input = defaultResource(name)) {
                if (name.equals(STORAGE) && generateKey) {
                    // A fresh install's only key; an existing file is never rewritten.
                    Files.writeString(file, AddressHashKeys.withGeneratedKey(
                            new String(input.readAllBytes(), StandardCharsets.UTF_8),
                            new SecureRandom()), StandardCharsets.UTF_8,
                            StandardOpenOption.CREATE_NEW);
                } else {
                    Files.copy(input, file);
                }
            }
        }
        return file;
    }

    static InputStream defaultResource(final String name) {
        final InputStream input = ConfigFiles.class.getResourceAsStream("/defaults/" + name);
        if (input == null) {
            throw new UncheckedIOException(
                    new IOException("the default " + name + " is missing from the jar"));
        }
        return input;
    }
}
