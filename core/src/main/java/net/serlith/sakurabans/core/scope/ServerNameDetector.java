package net.serlith.sakurabans.core.scope;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.Executor;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import net.serlith.sakurabans.api.validation.Validator;
import net.serlith.sakurabans.core.configuration.ConfigService;
import org.jspecify.annotations.Nullable;

public final class ServerNameDetector {

    static final String FILE_NAME = "detected-server-name.txt";

    private final Path file;
    private final ConfigService config;
    private final Executor worker;
    private final ComponentLogger logger;
    private volatile @Nullable String name;
    private volatile boolean detectedThisRun;

    public ServerNameDetector(final Path dataDirectory, final ConfigService config,
            final Executor worker, final ComponentLogger logger) {
        this.file = dataDirectory.resolve(FILE_NAME);
        this.config = config;
        this.worker = worker;
        this.logger = logger;
    }

    public void load() {
        if (!Files.isRegularFile(this.file)) {
            return;
        }
        try {
            final String stored = Files.readString(this.file, StandardCharsets.UTF_8).strip();
            this.name = Validator.normalizeIdentifier(stored).orElse(null);
            if (this.name == null) {
                this.logger.warn("Ignoring {}: \"{}\" isn't a server name", FILE_NAME, stored);
            }
        } catch (final IOException e) {
            this.logger.warn("Couldn't read {}", FILE_NAME, e);
        }
    }

    public Optional<String> name() {
        return Optional.ofNullable(this.name);
    }

    public boolean wanted() {
        return this.config.settings().server().name() == null && !this.detectedThisRun;
    }

    public boolean detected(final String reported) {
        if (!this.wanted()) {
            return false;
        }
        final Optional<String> normalized = Validator.normalizeIdentifier(reported);
        if (normalized.isEmpty()) {
            this.logger.warn("The proxy calls this server \"{}\", which isn't a valid server"
                    + " name (1-64 characters of a-z, 0-9, _ and -); set server.name instead",
                    reported);
            this.detectedThisRun = true;
            return false;
        }
        final String previous = this.name;
        this.name = normalized.get();
        this.detectedThisRun = true;
        if (normalized.get().equals(previous)) {
            return true;
        }
        if (previous == null) {
            this.logger.info("The proxy calls this server \"{}\"", normalized.get());
        } else {
            this.logger.warn("The proxy now calls this server \"{}\" (it was \"{}\")",
                    normalized.get(), previous);
        }
        this.worker.execute(() -> this.store(normalized.get()));
        return true;
    }

    private void store(final String value) {
        try {
            Files.writeString(this.file, value + System.lineSeparator(), StandardCharsets.UTF_8);
        } catch (final IOException e) {
            this.logger.warn("Couldn't write {}", FILE_NAME, e);
        }
    }
}
