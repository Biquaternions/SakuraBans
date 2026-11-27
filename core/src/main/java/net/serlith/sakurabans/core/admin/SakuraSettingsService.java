package net.serlith.sakurabans.core.admin;

import java.time.InstantSource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import net.serlith.sakurabans.api.model.Actor;
import net.serlith.sakurabans.api.model.GlobalScope;
import net.serlith.sakurabans.api.model.Scope;
import net.serlith.sakurabans.api.model.ServerScope;
import net.serlith.sakurabans.api.validation.FieldError;
import net.serlith.sakurabans.core.configuration.ConfigIssue;
import net.serlith.sakurabans.core.configuration.ConfigService;
import net.serlith.sakurabans.core.configuration.EnvironmentOverrides;
import net.serlith.sakurabans.core.configuration.ReloadResult;
import net.serlith.sakurabans.core.configuration.SettingsLayers;
import net.serlith.sakurabans.core.configuration.SettingsSchema;
import net.serlith.sakurabans.core.model.AdminResult;
import net.serlith.sakurabans.core.model.ApiValues;
import net.serlith.sakurabans.core.model.SubjectType;
import net.serlith.sakurabans.core.permission.Action;
import net.serlith.sakurabans.core.permission.AuthorizationService;
import net.serlith.sakurabans.core.storage.repository.InstanceRepository;
import net.serlith.sakurabans.core.storage.repository.OverrideRepository;
import net.serlith.sakurabans.core.sync.SyncPacket;
import net.serlith.sakurabans.core.sync.SyncService;
import net.serlith.sakurabans.core.util.Scopes;
import org.jspecify.annotations.Nullable;

public final class SakuraSettingsService {

    private static final String CATEGORIES = "scopes.categories";

    private final ConfigService config;
    private final OverrideRepository repository;
    private final AuthorizationService authorization;
    private final SyncService sync;
    private final InstantSource clock;
    private final ComponentLogger logger;
    private final AtomicReference<Map<Scope, Long>> versions = new AtomicReference<>(Map.of());

    public SakuraSettingsService(final ConfigService config, final OverrideRepository repository,
            final AuthorizationService authorization, final SyncService sync,
            final InstantSource clock, final ComponentLogger logger) {
        this.config = config;
        this.repository = repository;
        this.authorization = authorization;
        this.sync = sync;
        this.clock = clock;
        this.logger = logger;
    }

    public CompletableFuture<Void> reload() {
        return this.repository.settings().thenAccept(stored -> {
            final Map<Scope, Long> versions = new HashMap<>();
            final Map<Scope, Map<String, String>> values = new HashMap<>();
            stored.scopes().forEach((scope, settings) -> {
                versions.put(scope, settings.version());
                if (!settings.values().isEmpty()) {
                    values.put(scope, settings.values());
                }
            });
            this.versions.set(Map.copyOf(versions));
            if (values.equals(this.config.overrides())) {
                return;
            }
            if (this.config.applyOverrides(values)
                    instanceof ReloadResult.Failed(List<ConfigIssue> errors)) {
                this.logger.error("The setting overrides in the database don't make a valid"
                        + " configuration for this server, which keeps its last valid settings:"
                        + " {}", errors);
            }
        });
    }

    public List<SettingField> schema() {
        final List<SettingField> fields = new ArrayList<>();
        for (final SettingsSchema.Entry entry : SettingsSchema.ENTRIES) {
            fields.add(new SettingField(entry.key(), entry.type(),
                    this.config.defaultValue(entry.key()).orElse(""), entry.description(),
                    entry.options(), entry.min(), entry.max(), entry.editable(),
                    entry.editable(), entry.key().equals("commands.labels")));
        }
        return List.copyOf(fields);
    }

    /** The effective values here, by key; secrets left out. */
    public SettingsSnapshot settings() {
        final Map<String, Object> values = new LinkedHashMap<>();
        final Map<String, ValueOrigin> origins = new LinkedHashMap<>();
        for (final SettingsSchema.Entry entry : SettingsSchema.ENTRIES) {
            if (SettingsSchema.SECRET.contains(entry.key())) {
                continue;
            }
            this.config.value(entry.key()).ifPresent(value -> values.put(entry.key(), value));
            origins.put(entry.key(), this.origin(entry.key()));
        }
        return new SettingsSnapshot(this.version(), values, origins);
    }

    /** The global scope's overrides. */
    public SortedMap<String, String> overrides() {
        return this.overrides(GlobalScope.INSTANCE);
    }

    public SortedMap<String, String> overrides(final Scope scope) {
        return new TreeMap<>(this.config.overrides(scope));
    }

    public Map<Scope, Map<String, String>> allOverrides() {
        return this.config.overrides();
    }

    public List<EnvironmentOverrides.Applied> environment() {
        return this.config.environment();
    }

    public List<Scope> layers() {
        return this.config.layers();
    }

    public String fileValue(final String key) {
        return this.config.fileValue(key).map(String::valueOf).orElse("");
    }

    public Optional<Object> plainFileValue(final String key) {
        return this.config.fileValue(key);
    }

    public Optional<Object> plainValue(final String key) {
        return this.config.value(key);
    }

    public Optional<Object> plainDefault(final String key) {
        return this.config.defaultValue(key);
    }

    public ValueOrigin origin(final String key) {
        return this.config.source(key).isPresent() ? ValueOrigin.OVERRIDE
                : this.config.inFile(key) ? ValueOrigin.FILE : ValueOrigin.DEFAULT;
    }

    public Optional<Scope> source(final String key) {
        return this.config.source(key);
    }

    public long version() {
        return this.version(GlobalScope.INSTANCE);
    }

    public long version(final Scope scope) {
        return this.versions.get().getOrDefault(scope,
                scope instanceof GlobalScope ? 1L : 0L);
    }

    // Changes

    public CompletableFuture<AdminResult<SettingsSnapshot>> update(
            final Map<String, Object> patch, final long expectedVersion, final Actor actor) {
        return this.change(GlobalScope.INSTANCE, patch, Set.of(), expectedVersion, actor);
    }

    public CompletableFuture<AdminResult<SettingsSnapshot>> reset(final Set<String> keys,
            final long expectedVersion, final Actor actor) {
        return this.change(GlobalScope.INSTANCE, Map.of(), keys, expectedVersion, actor);
    }

    public CompletableFuture<AdminResult<SettingsSnapshot>> change(final Map<String, Object> set,
            final Set<String> reset, final long expectedVersion, final Actor actor) {
        return this.change(GlobalScope.INSTANCE, set, reset, expectedVersion, actor);
    }

    public CompletableFuture<AdminResult<SettingsSnapshot>> change(final Scope scope,
            final Map<String, Object> set, final Set<String> reset, final long expectedVersion,
            final Actor actor) {
        final Map<String, @Nullable String> changes = new HashMap<>();
        final List<FieldError> errors = this.prepare(scope, set, reset, changes);
        return this.write(scope, changes, errors, expectedVersion, actor);
    }

    /**
     * Every problem the change would have, without authorization or writing: unknown or
     * file-only keys, a global-only key in another scope, values that can't be stored, and a
     * configuration that would no longer load for this server. Paths are the setting keys.
     */
    public List<FieldError> validate(final Scope scope, final Map<String, Object> set,
            final Set<String> reset) {
        final Map<String, @Nullable String> changes = new HashMap<>();
        final List<FieldError> errors = this.prepare(scope, set, reset, changes);
        return errors.isEmpty() ? this.check(this.next(scope, changes)) : errors;
    }

    public Map<String, @Nullable String> effective(final Scope scope,
            final Map<String, Object> set, final Set<String> reset) {
        final Map<String, @Nullable String> changes = new HashMap<>();
        this.prepare(scope, set, reset, changes);
        final Map<String, String> current = this.config.overrides(scope);
        changes.entrySet().removeIf(change -> change.getValue() == null
                ? !current.containsKey(change.getKey())
                : change.getValue().equals(current.get(change.getKey())));
        return changes;
    }

    public @Nullable Object decoded(final String text) {
        return this.decode(text);
    }

    public String encode(final @Nullable Object value) {
        return this.config.encode(value);
    }

    // Other servers

    public record Effective(@Nullable Object value, @Nullable Scope scope) {
    }

    /**
     * Resolves a server's configuration exactly as that server does, from its registry row's
     * file values and the overrides in the database (protocol 5.1).
     */
    public Map<String, Effective> effectiveOn(final InstanceRepository.Instance server) {
        final Map<String, @Nullable Object> files = InstanceRegistry.fileValues(server);
        final List<Scope> scopes = this.scopesOf(server.serverName(), server.categories(),
                files.get(CATEGORIES));
        final Map<String, Effective> effective = new LinkedHashMap<>();
        for (final SettingsSchema.Entry entry : SettingsSchema.ENTRIES) {
            if (entry.editable()) {
                effective.put(entry.key(), this.resolve(entry.key(), scopes,
                        files.get(entry.key())));
            }
        }
        return effective;
    }

    /**
     * A key's value as a scope sees it: its override, else what it inherits. Global inherits
     * the default; a category, global; a server resolves as protocol 5.1 says, from its
     * registry row when there is one (else as an unlisted server with the default as file).
     */
    public @Nullable Object seen(final Scope scope, final String key,
            final InstanceRepository.@Nullable Instance server) {
        return switch (scope) {
            case GlobalScope global -> this.resolve(key, List.of(global),
                    this.config.defaultValue(key).orElse(null)).value();
            case ServerScope named when server != null ->
                    this.resolve(key, this.scopesOf(named.server(), server.categories(),
                            InstanceRegistry.fileValues(server).get(CATEGORIES)),
                            InstanceRegistry.fileValues(server).get(key)).value();
            case ServerScope named -> this.resolve(key, this.scopesOf(named.server(),
                    List.of(), Map.of()), this.config.defaultValue(key).orElse(null)).value();
            default -> this.resolve(key, List.of(scope, GlobalScope.INSTANCE),
                    this.config.defaultValue(key).orElse(null)).value();
        };
    }

    private List<Scope> scopesOf(final String server, final List<String> own,
            final @Nullable Object fileCategories) {
        final Object categories = Optional.ofNullable(this.config.overrides(GlobalScope.INSTANCE)
                .get(CATEGORIES)).map(this::decode).orElse(fileCategories);
        return SettingsLayers.scopes(server, own, categoriesOf(categories));
    }

    private Effective resolve(final String key, final List<Scope> scopes,
            final @Nullable Object file) {
        for (final Scope scope : scopes) {
            final String text = this.config.overrides(scope).get(key);
            if (text != null) {
                return new Effective(this.decode(text), scope);
            }
        }
        return new Effective(file, null);
    }

    public record Block(Scope scope, Map<String, Object> set, Set<String> reset) {
    }

    public record BlockError(int block, FieldError error) {
    }

    /**
     * Every problem of several scopes' changes made together: each block's own (unknown,
     * file-only and global-only keys, values that can't be stored), then whether the result
     * still loads for this server. Paths are the setting keys.
     */
    public List<BlockError> validateTogether(final List<Block> blocks) {
        final List<BlockError> errors = new ArrayList<>();
        final Map<Scope, Map<String, String>> next = new HashMap<>(this.config.overrides());
        for (int i = 0; i < blocks.size(); i++) {
            final Block block = blocks.get(i);
            final Map<String, @Nullable String> changes = new HashMap<>();
            for (final FieldError error : this.prepare(block.scope(), block.set(), block.reset(),
                    changes)) {
                errors.add(new BlockError(i, error));
            }
            final Map<String, String> values =
                    new HashMap<>(next.getOrDefault(block.scope(), Map.of()));
            changes.forEach((key, value) -> {
                if (value == null) {
                    values.remove(key);
                } else {
                    values.put(key, value);
                }
            });
            next.put(block.scope(), values);
        }
        if (!errors.isEmpty()) {
            return errors;
        }
        for (final FieldError error : this.check(next)) {
            int owner = -1;
            for (int i = 0; i < blocks.size() && owner < 0; i++) {
                if (blocks.get(i).set().containsKey(error.path())
                        || blocks.get(i).reset().contains(error.path())) {
                    owner = i;
                }
            }
            errors.add(new BlockError(owner, error));
        }
        return errors;
    }

    /**
     * The keys whose file value on that server differs from what the global layer gives
     * (its override, else the default): what publishing would change. Old value first.
     */
    public SortedMap<String, List<@Nullable Object>> publishable(
            final InstanceRepository.Instance server, final Set<String> keys) {
        final Map<String, @Nullable Object> files = InstanceRegistry.fileValues(server);
        final Map<String, String> global = this.config.overrides(GlobalScope.INSTANCE);
        final SortedMap<String, List<@Nullable Object>> diff = new TreeMap<>();
        for (final SettingsSchema.Entry entry : SettingsSchema.ENTRIES) {
            if (!entry.editable() || !keys.isEmpty() && !keys.contains(entry.key())
                    || !files.containsKey(entry.key())) {
                continue;
            }
            final Object now = global.containsKey(entry.key())
                    ? this.decode(global.get(entry.key()))
                    : this.config.defaultValue(entry.key()).orElse(null);
            final Object file = files.get(entry.key());
            if (!Objects.equals(normalize(now), normalize(file))) {
                final List<@Nullable Object> pair = new ArrayList<>();
                pair.add(now);
                pair.add(file);
                diff.put(entry.key(), pair);
            }
        }
        return diff;
    }

    /** Makes a server's file values the global overrides for these keys. */
    public CompletableFuture<AdminResult<SettingsSnapshot>> publish(
            final InstanceRepository.Instance server, final Set<String> keys,
            final long expectedVersion, final Actor actor) {
        final Map<String, @Nullable Object> files = InstanceRegistry.fileValues(server);
        final Map<String, Object> set = new LinkedHashMap<>();
        final List<FieldError> errors = new ArrayList<>();
        for (final String key : keys) {
            if (!files.containsKey(key)) {
                errors.add(FieldError.of(key, FieldError.Code.INVALID, "isn't among the file"
                        + " values of " + server.serverName()));
            } else {
                set.put(key, files.get(key));
            }
        }
        if (!errors.isEmpty()) {
            return CompletableFuture.completedFuture(new AdminResult.ValidationFailed<>(errors));
        }
        return this.change(GlobalScope.INSTANCE, set, Set.of(), expectedVersion, actor);
    }

    private @Nullable Object decode(final String text) {
        return this.config.decode(text).orElse(null);
    }

    private static @Nullable Object normalize(final @Nullable Object value) {
        return value instanceof Number number && number.doubleValue() == Math.rint(
                number.doubleValue()) ? (Object) number.longValue() : value;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, List<String>> categoriesOf(final @Nullable Object tree) {
        final Map<String, List<String>> categories = new LinkedHashMap<>();
        if (tree instanceof Map<?, ?> map) {
            map.forEach((category, servers) -> categories.put(String.valueOf(category),
                    servers instanceof List<?> list
                            ? list.stream().map(String::valueOf).toList() : List.of()));
        }
        return categories;
    }

    private List<FieldError> prepare(final Scope scope, final Map<String, Object> set,
            final Set<String> reset, final Map<String, @Nullable String> changes) {
        final List<FieldError> errors = new ArrayList<>();
        set.forEach((key, value) -> {
            if (this.editable(scope, key, errors)) {
                try {
                    changes.put(key, this.config.encode(value));
                } catch (final IllegalArgumentException e) {
                    errors.add(FieldError.of(key, FieldError.Code.INVALID, e.getMessage()));
                }
            }
        });
        for (final String key : reset) {
            if (this.editable(scope, key, errors)) {
                changes.put(key, null);
            }
        }
        return errors;
    }

    private Map<Scope, Map<String, String>> next(final Scope scope,
            final Map<String, @Nullable String> changes) {
        final Map<Scope, Map<String, String>> next = new HashMap<>(this.config.overrides());
        final Map<String, String> values = new HashMap<>(next.getOrDefault(scope, Map.of()));
        changes.forEach((key, value) -> {
            if (value == null) {
                values.remove(key);
            } else {
                values.put(key, value);
            }
        });
        next.put(scope, values);
        return next;
    }

    private List<FieldError> check(final Map<Scope, Map<String, String>> next) {
        final List<FieldError> invalid = new ArrayList<>();
        for (final ConfigIssue issue : this.config.checkOverrides(next)) {
            invalid.add(FieldError.of(SettingsSchema.key(issue.file(), issue.path()),
                    FieldError.Code.INVALID, issue.message()));
        }
        return invalid;
    }

    private boolean editable(final Scope scope, final String key,
            final List<FieldError> errors) {
        final SettingsSchema.Entry entry = SettingsSchema.entry(key).orElse(null);
        if (entry == null) {
            errors.add(FieldError.of(key, FieldError.Code.INVALID, "is not a setting"));
            return false;
        }
        if (!entry.editable()) {
            errors.add(FieldError.of(key, FieldError.Code.NOT_ALLOWED,
                    "differs per server and is only set in config.yml"));
            return false;
        }
        if (entry.level() == SettingsSchema.Level.GLOBAL && !(scope instanceof GlobalScope)) {
            errors.add(FieldError.of(key, FieldError.Code.NOT_ALLOWED,
                    "must be the same everywhere, so only the global scope may set it, not "
                            + Scopes.format(scope)));
            return false;
        }
        return true;
    }

    private CompletableFuture<AdminResult<SettingsSnapshot>> write(final Scope scope,
            final Map<String, @Nullable String> changes, final List<FieldError> errors,
            final long expectedVersion, final Actor actor) {
        final Actor checked;
        try {
            checked = ApiValues.actor(actor);
        } catch (final RuntimeException e) {
            return CompletableFuture.failedFuture(e);
        }
        return this.authorization.check(checked, new Action.Manage(Action.Manage.Area.SETTINGS))
                .thenCompose(decision -> {
                    if (!decision.allowed()) {
                        return CompletableFuture.completedFuture(
                                new AdminResult.Forbidden<>());
                    }
                    if (!errors.isEmpty()) {
                        return CompletableFuture.completedFuture(
                                new AdminResult.ValidationFailed<>(errors));
                    }
                    final Map<Scope, Map<String, String>> next = this.next(scope, changes);
                    final List<FieldError> invalid = this.check(next);
                    if (!invalid.isEmpty()) {
                        return CompletableFuture.completedFuture(
                                new AdminResult.ValidationFailed<>(invalid));
                    }
                    return this.repository.changeSettings(scope, changes, expectedVersion,
                            checked, this.clock.instant()).thenApply(written -> {
                                if (written.conflict()) {
                                    return new AdminResult.Conflict<>(written.version());
                                }
                                final Map<Scope, Long> versions =
                                        new HashMap<>(this.versions.get());
                                versions.put(scope, written.version());
                                this.versions.set(Map.copyOf(versions));
                                this.config.applyOverrides(next);
                                this.sync.publish(new SyncPacket.EntityChangedPacket(
                                        SubjectType.SETTINGS));
                                return new AdminResult.Ok<>(this.settings());
                            });
                });
    }
}
