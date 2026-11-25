package net.serlith.sakurabans.core.configuration;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import net.serlith.sakurabans.core.configuration.file.LimitsConfig;
import net.serlith.sakurabans.core.configuration.file.MainConfig;
import net.serlith.sakurabans.core.configuration.file.StorageConfig;
import net.serlith.sakurabans.core.configuration.file.WebhooksConfig;
import net.serlith.sakurabans.core.configuration.settings.LimitSettings;
import net.serlith.sakurabans.core.configuration.settings.Settings;
import net.serlith.sakurabans.core.configuration.settings.StorageSettings;
import net.serlith.sakurabans.core.configuration.settings.WebhookSettings;
import org.jspecify.annotations.Nullable;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

public final class ConfigService {

    private final Path dataDirectory;
    private final ComponentLogger logger;
    private final AtomicReference<@Nullable Settings> settings = new AtomicReference<>();
    private final AtomicReference<@Nullable StorageSettings> storage = new AtomicReference<>();
    private final AtomicReference<@Nullable LimitSettings> limits = new AtomicReference<>();
    private final AtomicReference<@Nullable WebhookSettings> webhooks = new AtomicReference<>();
    private final AtomicReference<Map<String, String>> overrides =
            new AtomicReference<>(Map.of());
    private final AtomicReference<@Nullable ConfigurationNode> fileNode =
            new AtomicReference<>();
    private final AtomicReference<@Nullable ConfigurationNode> effectiveNode =
            new AtomicReference<>();

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

    public WebhookSettings webhooks() {
        final WebhookSettings current = this.webhooks.get();
        if (current == null) {
            throw new IllegalStateException("the configuration isn't loaded yet");
        }
        return current;
    }

    public void load() throws ConfigException {
        final Loaded loaded = this.read(this.overrides.get());
        if (!loaded.errors().isEmpty()) {
            throw new ConfigException(loaded.errors());
        }
        this.settings.set(loaded.settings());
        this.storage.set(loaded.storage());
        this.limits.set(loaded.limits());
        this.webhooks.set(loaded.webhooks());
        this.fileNode.set(loaded.fileNode());
        this.effectiveNode.set(loaded.effectiveNode());
        this.logWarnings(loaded.warnings());
    }

    public ReloadResult reload() {
        return this.reloadWith(this.overrides.get());
    }

    public ReloadResult applyOverrides(final Map<String, String> next) {
        return this.reloadWith(Map.copyOf(next));
    }

    public List<ConfigIssue> checkOverrides(final Map<String, String> next) {
        return this.read(Map.copyOf(next)).errors();
    }

    public Map<String, String> overrides() {
        return this.overrides.get();
    }

    public String encode(final @Nullable Object value) {
        try {
            final YamlConfigurationLoader.Builder builder = ConfigFiles.builder();
            final ConfigurationNode node = builder.build().createNode();
            node.set(value);
            return builder.buildAndSaveString(node);
        } catch (final ConfigurateException e) {
            throw new IllegalArgumentException(describe(e), e);
        }
    }

    public Optional<Object> value(final String key) {
        final ConfigurationNode node = this.effectiveNode.get();
        if (node != null) {
            final ConfigurationNode child = node.node(path(key));
            if (!child.virtual()) {
                return Optional.ofNullable(plain(child));
            }
        }
        return this.defaultValue(key);
    }

    public Optional<Object> defaultValue(final String key) {
        try {
            final ConfigurationNode defaults = ConfigFiles.builder().build().createNode();
            defaults.set(MainConfig.class, new MainConfig());
            final ConfigurationNode child = defaults.node(path(key));
            return child.virtual() ? Optional.empty() : Optional.ofNullable(plain(child));
        } catch (final ConfigurateException e) {
            throw new IllegalStateException(e);
        }
    }

    public boolean inFile(final String key) {
        final ConfigurationNode node = this.fileNode.get();
        return node != null && !node.node(path(key)).virtual();
    }

    private static Object[] path(final String key) {
        return key.split("\\.");
    }

    private static @Nullable Object plain(final ConfigurationNode node) {
        if (node.isMap()) {
            final Map<String, @Nullable Object> map = new LinkedHashMap<>();
            node.childrenMap().forEach((name, child) ->
                    map.put(String.valueOf(name), plain(child)));
            return map;
        }
        if (node.isList()) {
            final List<@Nullable Object> list = new ArrayList<>();
            node.childrenList().forEach(child -> list.add(plain(child)));
            return list;
        }
        return node.raw();
    }

    private ReloadResult reloadWith(final Map<String, String> next) {
        final Loaded loaded = this.read(next);
        if (!loaded.errors().isEmpty()) {
            return new ReloadResult.Failed(loaded.errors());
        }
        final List<String> restartRequired = new ArrayList<>();
        if (!loaded.storage().equals(this.storage.get())) {
            restartRequired.add(ConfigFiles.STORAGE);
        }
        this.settings.set(loaded.settings());
        this.limits.set(loaded.limits());
        this.webhooks.set(loaded.webhooks());
        this.fileNode.set(loaded.fileNode());
        this.effectiveNode.set(loaded.effectiveNode());
        this.overrides.set(next);
        this.logWarnings(loaded.warnings());
        return new ReloadResult.Reloaded(List.copyOf(restartRequired), loaded.warnings());
    }

    private record Loaded(@Nullable Settings settings, @Nullable StorageSettings storage,
            @Nullable LimitSettings limits, @Nullable WebhookSettings webhooks,
            @Nullable ConfigurationNode fileNode, @Nullable ConfigurationNode effectiveNode,
            List<ConfigIssue> errors, List<ConfigIssue> warnings) {

        @Override
        public WebhookSettings webhooks() {
            if (this.webhooks == null) {
                throw new IllegalStateException("no webhook settings: " + this.errors);
            }
            return this.webhooks;
        }

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

    private Loaded read(final Map<String, String> overrides) {
        final List<ConfigIssue> errors = new ArrayList<>();
        final List<ConfigIssue> warnings = new ArrayList<>();
        final AtomicReference<@Nullable ConfigurationNode> file = new AtomicReference<>();
        final AtomicReference<@Nullable ConfigurationNode> effective = new AtomicReference<>();
        final MainConfig main = this.readFile(ConfigFiles.CONFIG, MainConfig.class, errors,
                warnings, node -> {
                    file.set(node.copy());
                    this.override(node, overrides, errors);
                    effective.set(node.copy());
                });
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
        final WebhooksConfig webhooksConfig =
                this.readFile(ConfigFiles.WEBHOOKS, WebhooksConfig.class, errors, warnings);
        WebhookSettings webhookSettings = null;
        if (webhooksConfig != null) {
            final SettingsMapper.Mapped<WebhookSettings> mapped =
                    SettingsMapper.webhooks(webhooksConfig);
            errors.addAll(mapped.errors());
            webhookSettings = mapped.value();
        }
        return new Loaded(settings, storageSettings, limitSettings, webhookSettings, file.get(),
                effective.get(), List.copyOf(errors), List.copyOf(warnings));
    }

    private void override(final ConfigurationNode node, final Map<String, String> overrides,
            final List<ConfigIssue> errors) {
        final YamlConfigurationLoader.Builder builder = ConfigFiles.builder();
        overrides.forEach((key, text) -> {
            try {
                node.node(path(key)).from(builder.buildAndLoadString(text));
            } catch (final ConfigurateException e) {
                errors.add(ConfigIssue.error(ConfigFiles.CONFIG, key,
                        "the stored override can't be read: " + describe(e)));
            }
        });
    }

    private <T> @Nullable T readFile(final String name, final Class<T> model,
            final List<ConfigIssue> errors, final List<ConfigIssue> warnings) {
        return this.readFile(name, model, errors, warnings, node -> { });
    }

    private <T> @Nullable T readFile(final String name, final Class<T> model,
            final List<ConfigIssue> errors, final List<ConfigIssue> warnings,
            final Consumer<ConfigurationNode> prepare) {
        final ConfigurationNode node;
        try {
            final Path file = ConfigFiles.ensureDefault(this.dataDirectory, name);
            node = ConfigFiles.loader(file).load();
        } catch (final IOException e) {
            errors.add(ConfigIssue.error(name, "", describe(e)));
            return null;
        }
        prepare.accept(node);
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
