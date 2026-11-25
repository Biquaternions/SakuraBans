package net.serlith.sakurabans.core.configuration;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import net.serlith.sakurabans.core.configuration.file.LimitsConfig;
import net.serlith.sakurabans.core.configuration.file.MainConfig;
import net.serlith.sakurabans.core.configuration.file.StorageConfig;
import net.serlith.sakurabans.core.configuration.settings.LimitSettings;
import net.serlith.sakurabans.core.configuration.settings.Settings;
import net.serlith.sakurabans.core.configuration.settings.StorageSettings;
import org.jspecify.annotations.Nullable;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.ConfigurationNode;

public final class ConfigService {

    private final Path dataDirectory;
    private final ComponentLogger logger;
    private final AtomicReference<@Nullable Settings> settings = new AtomicReference<>();
    private final AtomicReference<@Nullable StorageSettings> storage = new AtomicReference<>();
    private final AtomicReference<@Nullable LimitSettings> limits = new AtomicReference<>();

    public ConfigService(final Path dataDirectory, final ComponentLogger logger) {
        this.dataDirectory = dataDirectory;
        this.logger = logger;
    }

    public Settings settings() {
        final Settings current = this.settings.get();
        if (current == null) {
            throw new IllegalStateException("the configuration isn't loaded yet");
        }
        return current;
    }

    public StorageSettings storage() {
        final StorageSettings current = this.storage.get();
        if (current == null) {
            throw new IllegalStateException("the configuration isn't loaded yet");
        }
        return current;
    }

    public LimitSettings limits() {
        final LimitSettings current = this.limits.get();
        if (current == null) {
            throw new IllegalStateException("the configuration isn't loaded yet");
        }
        return current;
    }

    public void load() throws ConfigException {
        final Loaded loaded = this.read();
        if (!loaded.errors().isEmpty()) {
            throw new ConfigException(loaded.errors());
        }
        this.settings.set(loaded.settings());
        this.storage.set(loaded.storage());
        this.limits.set(loaded.limits());
        this.logWarnings(loaded.warnings());
    }

    public ReloadResult reload() {
        final Loaded loaded = this.read();
        if (!loaded.errors().isEmpty()) {
            return new ReloadResult.Failed(loaded.errors());
        }
        final List<String> restartRequired = new ArrayList<>();
        if (!loaded.storage().equals(this.storage.get())) {
            restartRequired.add(ConfigFiles.STORAGE);
        }
        this.settings.set(loaded.settings());
        this.limits.set(loaded.limits());
        this.logWarnings(loaded.warnings());
        return new ReloadResult.Reloaded(List.copyOf(restartRequired), loaded.warnings());
    }

    private record Loaded(@Nullable Settings settings, @Nullable StorageSettings storage,
            @Nullable LimitSettings limits, List<ConfigIssue> errors, List<ConfigIssue> warnings) {

        @Override
        public LimitSettings limits() {
            if (this.limits == null) {
                throw new IllegalStateException("no limit settings: " + this.errors);
            }
            return this.limits;
        }

        @Override
        public Settings settings() {
            if (this.settings == null) {
                throw new IllegalStateException("no settings: " + this.errors);
            }
            return this.settings;
        }

        @Override
        public StorageSettings storage() {
            if (this.storage == null) {
                throw new IllegalStateException("no storage settings: " + this.errors);
            }
            return this.storage;
        }
    }

    private Loaded read() {
        final List<ConfigIssue> errors = new ArrayList<>();
        final List<ConfigIssue> warnings = new ArrayList<>();
        final MainConfig main =
                this.readFile(ConfigFiles.CONFIG, MainConfig.class, errors, warnings);
        final StorageConfig storageConfig =
                this.readFile(ConfigFiles.STORAGE, StorageConfig.class, errors, warnings);
        final LimitsConfig limitsConfig =
                this.readFile(ConfigFiles.LIMITS, LimitsConfig.class, errors, warnings);
        Settings settings = null;
        StorageSettings storageSettings = null;
        if (main != null) {
            final SettingsMapper.Mapped<Settings> mapped = SettingsMapper.settings(main);
            errors.addAll(mapped.errors());
            settings = mapped.value();
        }
        if (storageConfig != null) {
            final SettingsMapper.Mapped<StorageSettings> mapped =
                    SettingsMapper.storage(storageConfig);
            errors.addAll(mapped.errors());
            storageSettings = mapped.value();
        }
        LimitSettings limitSettings = null;
        if (limitsConfig != null) {
            final SettingsMapper.Mapped<LimitSettings> mapped = SettingsMapper.limits(limitsConfig);
            errors.addAll(mapped.errors());
            limitSettings = mapped.value();
        }
        return new Loaded(settings, storageSettings, limitSettings, List.copyOf(errors),
                List.copyOf(warnings));
    }

    private <T> @Nullable T readFile(final String name, final Class<T> model,
            final List<ConfigIssue> errors, final List<ConfigIssue> warnings) {
        final ConfigurationNode node;
        try {
            final Path file = ConfigFiles.ensureDefault(this.dataDirectory, name);
            node = ConfigFiles.loader(file).load();
        } catch (final IOException e) {
            errors.add(ConfigIssue.error(name, "", describe(e)));
            return null;
        }
        final ConfigValidator.Result result = ConfigValidator.validate(name, node, (Type) model);
        for (final ConfigIssue issue : result.issues()) {
            (issue.severity() == ConfigIssue.Severity.ERROR ? errors : warnings).add(issue);
        }
        if (!result.missingKeys().isEmpty()) {
            warnings.add(ConfigIssue.warning(name, "", "missing keys use their defaults: "
                    + String.join(", ", result.missingKeys())));
        }
        if (result.hasErrors()) {
            return null;
        }
        try {
            return node.get(model);
        } catch (final ConfigurateException e) {
            errors.add(ConfigIssue.error(name, "", describe(e)));
            return null;
        }
    }

    private void logWarnings(final List<ConfigIssue> warnings) {
        warnings.forEach(warning -> this.logger.warn("Configuration: {}", warning));
    }

    private static String describe(final Exception e) {
        final String message = e.getMessage();
        return message == null ? e.getClass().getSimpleName() : message.strip();
    }
}
