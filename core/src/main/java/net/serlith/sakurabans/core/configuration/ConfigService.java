package net.serlith.sakurabans.core.configuration;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import net.serlith.sakurabans.api.model.GlobalScope;
import net.serlith.sakurabans.api.model.Scope;
import net.serlith.sakurabans.core.configuration.file.ImportConfig;
import net.serlith.sakurabans.core.configuration.file.LimitsConfig;
import net.serlith.sakurabans.core.configuration.file.MainConfig;
import net.serlith.sakurabans.core.configuration.file.StorageConfig;
import net.serlith.sakurabans.core.configuration.file.WebhooksConfig;
import net.serlith.sakurabans.core.configuration.settings.ImportSettings;
import net.serlith.sakurabans.core.configuration.settings.LimitSettings;
import net.serlith.sakurabans.core.configuration.settings.Settings;
import net.serlith.sakurabans.core.configuration.settings.StorageSettings;
import net.serlith.sakurabans.core.configuration.settings.WebhookSettings;
import net.serlith.sakurabans.core.util.Scopes;
import org.jspecify.annotations.Nullable;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

public final class ConfigService {

    private static final String CATEGORIES = "scopes.categories";

    private final Path dataDirectory;
    private final ComponentLogger logger;
    private final EnvironmentOverrides environment;
    private final AtomicReference<@Nullable Settings> settings = new AtomicReference<>();
    private final AtomicReference<@Nullable StorageSettings> storage = new AtomicReference<>();
    private final AtomicReference<@Nullable LimitSettings> limits = new AtomicReference<>();
    private final AtomicReference<@Nullable WebhookSettings> webhooks = new AtomicReference<>();
    private final AtomicReference<Map<Scope, Map<String, String>>> overrides =
            new AtomicReference<>(Map.of());
    private final AtomicReference<Resolution> resolution =
            new AtomicReference<>(new Resolution(List.of(GlobalScope.INSTANCE), Map.of()));
    /** By file: what the files say, and the result with this server's overrides. */
    private final AtomicReference<Map<String, ConfigurationNode>> fileNodes =
            new AtomicReference<>(Map.of());
    private final AtomicReference<Map<String, ConfigurationNode>> effectiveNodes =
            new AtomicReference<>(Map.of());

    public ConfigService(final Path dataDirectory, final ComponentLogger logger) {
        this(dataDirectory, logger, Map.of());
    }

    public ConfigService(final Path dataDirectory, final ComponentLogger logger,
            final Map<String, String> environment) {
        this.dataDirectory = dataDirectory;
        this.logger = logger;
        this.environment = EnvironmentOverrides.of(environment);
    }

    public List<EnvironmentOverrides.Applied> environment() {
        return this.environment.applied();
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

    public ImportSettings imports() throws ConfigException {
        final List<ConfigIssue> errors = new ArrayList<>();
        final List<ConfigIssue> warnings = new ArrayList<>();
        final ImportConfig file =
                this.readFile(ConfigFiles.IMPORT, ImportConfig.class, errors, warnings);
        ImportSettings imports = null;
        if (file != null) {
            final SettingsMapper.Mapped<ImportSettings> mapped = SettingsMapper.imports(file);
            errors.addAll(mapped.errors());
            imports = mapped.value();
        }
        this.logWarnings(warnings);
        if (imports == null || !errors.isEmpty()) {
            throw new ConfigException(errors);
        }
        return imports;
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
        this.fileNodes.set(loaded.fileNodes());
        this.effectiveNodes.set(loaded.effectiveNodes());
        this.resolution.set(loaded.resolution());
        this.logWarnings(loaded.warnings());
        this.logEnvironment();
    }

    /** The names of what the environment sets, never a value. */
    private void logEnvironment() {
        final List<EnvironmentOverrides.Applied> applied = this.environment.applied();
        if (!applied.isEmpty()) {
            this.logger.info("The environment sets {} file-level settings, over the files: {}"
                    + " (values aren't logged; /sb settings list --effective names them too)",
                    applied.size(), String.join(", ", applied.stream()
                            .map(EnvironmentOverrides.Applied::source).toList()));
        }
    }

    public ReloadResult reload() {
        return this.reloadWith(this.overrides.get());
    }

    /** Every scope's overrides (key to YAML text), as the database holds them. */
    public ReloadResult applyOverrides(final Map<Scope, Map<String, String>> next) {
        return this.reloadWith(copy(next));
    }

    /** A dry run: the errors this server's configuration would have with these overrides. */
    public List<ConfigIssue> checkOverrides(final Map<Scope, Map<String, String>> next) {
        return this.read(copy(next)).errors();
    }

    public Map<Scope, Map<String, String>> overrides() {
        return this.overrides.get();
    }

    public Map<String, String> overrides(final Scope scope) {
        return this.overrides.get().getOrDefault(scope, Map.of());
    }

    /** The scopes that apply to this server as last resolved, the winning one first. */
    public List<Scope> layers() {
        return this.resolution.get().scopes();
    }

    /** The scope whose override gives a key its effective value here, if any. */
    public Optional<Scope> source(final String key) {
        return Optional.ofNullable(this.resolution.get().sources().get(key));
    }

    private static Map<Scope, Map<String, String>> copy(
            final Map<Scope, Map<String, String>> overrides) {
        final Map<Scope, Map<String, String>> copy = new HashMap<>();
        overrides.forEach((scope, values) -> {
            if (!values.isEmpty()) {
                copy.put(scope, Map.copyOf(values));
            }
        });
        return Map.copyOf(copy);
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

    /** A stored override's YAML text as a plain tree, or empty when it can't be read. */
    public Optional<Object> decode(final String text) {
        try {
            return Optional.ofNullable(plain(ConfigFiles.builder().buildAndLoadString(text)));
        } catch (final ConfigurateException e) {
            return Optional.empty();
        }
    }

    /** A key's effective value here; {@code limits:} and {@code webhooks:} keys too. */
    public Optional<Object> value(final String key) {
        final ConfigurationNode node = this.effectiveNodes.get().get(SettingsSchema.file(key));
        if (node != null) {
            final ConfigurationNode child = node.node(path(key));
            if (!child.virtual()) {
                return Optional.ofNullable(plain(child));
            }
        }
        return this.defaultValue(key);
    }

    public Optional<Object> defaultValue(final String key) {
        final ConfigurationNode child = this.defaults(SettingsSchema.file(key)).node(path(key));
        return child.virtual() ? Optional.empty() : Optional.ofNullable(plain(child));
    }

    /** A file's model defaults as a tree. */
    private ConfigurationNode defaults(final String file) {
        try {
            final ConfigurationNode defaults = ConfigFiles.builder().build().createNode();
            switch (file) {
                case ConfigFiles.LIMITS -> defaults.set(LimitsConfig.class, new LimitsConfig());
                case ConfigFiles.WEBHOOKS ->
                        defaults.set(WebhooksConfig.class, new WebhooksConfig());
                default -> defaults.set(MainConfig.class, new MainConfig());
            }
            return defaults;
        } catch (final ConfigurateException e) {
            throw new IllegalStateException(e);
        }
    }

    /** The value this server's file gives a key, or its default: what applies without one. */
    public Optional<Object> fileValue(final String key) {
        final ConfigurationNode node = this.fileNodes.get().get(SettingsSchema.file(key));
        if (node != null) {
            final ConfigurationNode child = node.node(path(key));
            if (!child.virtual()) {
                return Optional.ofNullable(plain(child));
            }
        }
        return this.defaultValue(key);
    }

    public boolean inFile(final String key) {
        final ConfigurationNode node = this.fileNodes.get().get(SettingsSchema.file(key));
        return node != null && !node.node(path(key)).virtual();
    }

    private static Object[] path(final String key) {
        return SettingsSchema.path(key).split("\\.");
    }

    public static @Nullable Object plain(final ConfigurationNode node) {
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

    private ReloadResult reloadWith(final Map<Scope, Map<String, String>> next) {
        final Loaded loaded = this.read(next);
        if (!loaded.errors().isEmpty()) {
            return new ReloadResult.Failed(loaded.errors());
        }
        final List<String> restartRequired = new ArrayList<>();
        if (!loaded.storage().equals(this.storage.get())) {
            restartRequired.add(ConfigFiles.STORAGE);
        }
        final Settings.Commands before = this.settings().commands();
        final Settings.Commands after = loaded.settings().commands();
        if (!before.labels().equals(after.labels())) {
            restartRequired.add("commands.labels");
        }
        // The database is loaded at start only where GeoIP is on.
        if (this.settings().geoip().enabled() != loaded.settings().geoip().enabled()) {
            restartRequired.add("geoip.enabled");
        }
        this.settings.set(loaded.settings());
        this.limits.set(loaded.limits());
        this.webhooks.set(loaded.webhooks());
        this.fileNodes.set(loaded.fileNodes());
        this.effectiveNodes.set(loaded.effectiveNodes());
        this.resolution.set(loaded.resolution());
        this.overrides.set(next);
        this.logWarnings(loaded.warnings());
        return new ReloadResult.Reloaded(List.copyOf(restartRequired), loaded.warnings());
    }

    private record Loaded(@Nullable Settings settings, @Nullable StorageSettings storage,
            @Nullable LimitSettings limits, @Nullable WebhookSettings webhooks,
            Map<String, ConfigurationNode> fileNodes,
            Map<String, ConfigurationNode> effectiveNodes,
            Resolution resolution, List<ConfigIssue> errors, List<ConfigIssue> warnings) {

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

    /** Which scopes applied, the winning one first, and which scope gave each key. */
    private record Resolution(List<Scope> scopes, Map<String, Scope> sources) {

        Resolution {
            scopes = List.copyOf(scopes);
            sources = Map.copyOf(sources);
        }
    }

    private Loaded read(final Map<Scope, Map<String, String>> overrides) {
        final List<ConfigIssue> errors = new ArrayList<>(this.environment.errors());
        final List<ConfigIssue> warnings = new ArrayList<>();
        final Map<String, ConfigurationNode> files = new HashMap<>();
        final Map<String, ConfigurationNode> effective = new HashMap<>();
        final Map<String, Scope> sources = new HashMap<>();
        final AtomicReference<List<Scope>> scopes =
                new AtomicReference<>(List.of(GlobalScope.INSTANCE));
        final MainConfig main = this.readFile(ConfigFiles.CONFIG, MainConfig.class, errors,
                warnings, node -> {
                    // First: the scopes and everything after read this server's name from it.
                    this.environment.apply(ConfigFiles.CONFIG, node);
                    files.put(ConfigFiles.CONFIG, node.copy());
                    scopes.set(this.scopes(node, overrides));
                    this.layer(node, ConfigFiles.CONFIG, overrides, scopes.get(), sources,
                            errors);
                    effective.put(ConfigFiles.CONFIG, node.copy());
                });
        final StorageConfig storageConfig = this.readFile(ConfigFiles.STORAGE,
                StorageConfig.class, errors, warnings,
                node -> this.environment.apply(ConfigFiles.STORAGE, node));
        final LimitsConfig limitsConfig = this.readFile(ConfigFiles.LIMITS, LimitsConfig.class,
                errors, warnings, node -> {
                    files.put(ConfigFiles.LIMITS, node.copy());
                    this.layer(node, ConfigFiles.LIMITS, overrides, scopes.get(), sources,
                            errors);
                    effective.put(ConfigFiles.LIMITS, node.copy());
                });
        Settings settings = null;
        StorageSettings storageSettings = null;
        if (main != null) {
            final SettingsMapper.Mapped<Settings> mapped = SettingsMapper.settings(main);
            errors.addAll(mapped.errors());
            warnings.addAll(mapped.warnings());
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
        final WebhooksConfig webhooksConfig = this.readFile(ConfigFiles.WEBHOOKS,
                WebhooksConfig.class, errors, warnings, node -> {
                    files.put(ConfigFiles.WEBHOOKS, node.copy());
                    this.layer(node, ConfigFiles.WEBHOOKS, overrides, scopes.get(), sources,
                            errors);
                    effective.put(ConfigFiles.WEBHOOKS, node.copy());
                });
        WebhookSettings webhookSettings = null;
        if (webhooksConfig != null) {
            final SettingsMapper.Mapped<WebhookSettings> mapped =
                    SettingsMapper.webhooks(webhooksConfig);
            errors.addAll(mapped.errors());
            webhookSettings = mapped.value();
        }
        final Resolution resolved = new Resolution(scopes.get(), sources);
        // Errors name the scope a bad value came from.
        final List<ConfigIssue> sourced = errors.stream().map(this.environment::label)
                .map(issue -> sourced(issue, resolved)).toList();
        return new Loaded(settings, storageSettings, limitSettings, webhookSettings,
                Map.copyOf(files), Map.copyOf(effective), resolved, sourced,
                List.copyOf(warnings));
    }

    private static ConfigIssue sourced(final ConfigIssue issue, final Resolution resolution) {
        for (final Map.Entry<String, Scope> source : resolution.sources().entrySet()) {
            if (!SettingsSchema.file(source.getKey()).equals(issue.file())) {
                continue;
            }
            final String key = SettingsSchema.path(source.getKey());
            if (issue.path().equals(key) || issue.path().startsWith(key + ".")
                    || issue.path().startsWith(key + "[")) {
                return new ConfigIssue(issue.file(), issue.path(), issue.message() + " (from "
                        + Scopes.format(source.getValue()) + ")", issue.severity());
            }
        }
        return issue;
    }

    /**
     * The scopes that reach this server, the winning one first (protocol 5.1). Category
     * membership comes from the effective {@code scopes.categories}, which only the global
     * scope may override, so it is resolved first.
     */
    private List<Scope> scopes(final ConfigurationNode node,
            final Map<Scope, Map<String, String>> overrides) {
        ConfigurationNode categoriesNode = node.node("scopes", "categories");
        final String categoriesText = overrides.getOrDefault(GlobalScope.INSTANCE, Map.of())
                .get(CATEGORIES);
        if (categoriesText != null) {
            try {
                categoriesNode = ConfigFiles.builder().buildAndLoadString(categoriesText);
            } catch (final ConfigurateException e) {
                // Reported when the global layer is applied.
            }
        }
        final Map<String, List<String>> categories = new LinkedHashMap<>();
        categoriesNode.childrenMap().forEach((category, servers) -> categories.put(
                String.valueOf(category), servers.childrenList().stream()
                        .map(server -> String.valueOf(server.raw())).toList()));
        final List<String> own = node.node("server", "categories").childrenList().stream()
                .map(category -> String.valueOf(category.raw())).toList();
        return SettingsLayers.scopes(node.node("server", "name").getString(""), own,
                categories);
    }

    /**
     * Applies one file's overrides: global, then the categories from the last to the first,
     * then the server, so the first one wins. A value replaces the whole value at its key.
     */
    private void layer(final ConfigurationNode node, final String file,
            final Map<Scope, Map<String, String>> overrides, final List<Scope> scopes,
            final Map<String, Scope> sources, final List<ConfigIssue> errors) {
        final YamlConfigurationLoader.Builder builder = ConfigFiles.builder();
        for (int i = scopes.size() - 1; i >= 0; i--) {
            final Scope scope = scopes.get(i);
            overrides.getOrDefault(scope, Map.of()).forEach((key, text) -> {
                if (!SettingsSchema.file(key).equals(file)) {
                    return;
                }
                try {
                    // An override inside a section the file leaves out (webhook routes)
                    // mustn't drop that section's defaults.
                    final Object top = path(key)[0];
                    if (node.node(top).virtual()) {
                        node.node(top).from(this.defaults(file).node(top));
                    }
                    node.node(path(key)).from(builder.buildAndLoadString(text));
                    sources.put(key, scope);
                } catch (final ConfigurateException e) {
                    errors.add(ConfigIssue.error(file, SettingsSchema.path(key),
                            "the stored override of " + Scopes.format(scope)
                                    + " can't be read"));
                }
            });
        }
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
            final boolean fresh = Files.notExists(this.dataDirectory.resolve(name));
            // A key the environment supplies isn't generated into the file as well.
            final boolean keyFromEnvironment = name.equals(ConfigFiles.STORAGE)
                    && this.environment.supplies(ConfigFiles.STORAGE, "address-hash-key");
            final Path file = ConfigFiles.ensureDefault(this.dataDirectory, name,
                    !keyFromEnvironment);
            if (fresh && name.equals(ConfigFiles.STORAGE) && !keyFromEnvironment) {
                this.logger.info("Created storage.yml with a new address-hash-key. Every server"
                        + " that shares a database needs the same key: copy that line to the"
                        + " storage.yml of the others before they first start.");
            }
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
