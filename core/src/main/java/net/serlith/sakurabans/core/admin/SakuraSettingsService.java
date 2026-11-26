package net.serlith.sakurabans.core.admin;

import java.time.InstantSource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import net.serlith.sakurabans.api.model.Actor;
import net.serlith.sakurabans.api.validation.FieldError;
import net.serlith.sakurabans.core.configuration.ConfigIssue;
import net.serlith.sakurabans.core.configuration.ConfigService;
import net.serlith.sakurabans.core.configuration.ReloadResult;
import net.serlith.sakurabans.core.configuration.SettingsSchema;
import net.serlith.sakurabans.core.model.AdminResult;
import net.serlith.sakurabans.core.model.ApiValues;
import net.serlith.sakurabans.core.model.SubjectType;
import net.serlith.sakurabans.core.permission.Action;
import net.serlith.sakurabans.core.permission.AuthorizationService;
import net.serlith.sakurabans.core.storage.repository.OverrideRepository;
import net.serlith.sakurabans.core.sync.SyncPacket;
import net.serlith.sakurabans.core.sync.SyncService;
import org.jspecify.annotations.Nullable;

public final class SakuraSettingsService {

    static final String ENTITY = "settings";

    private final ConfigService config;
    private final OverrideRepository repository;
    private final AuthorizationService authorization;
    private final SyncService sync;
    private final InstantSource clock;
    private final ComponentLogger logger;
    private final AtomicLong version = new AtomicLong(1);

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
            this.version.set(stored.version());
            if (stored.values().equals(this.config.overrides())) {
                return;
            }
            if (this.config.applyOverrides(
                    stored.values()) instanceof ReloadResult.Failed(List<ConfigIssue> errors)) {
                this.logger.error("The network settings in the database don't fit this"
                        + " server's config.yml and were left out: {}", errors);
            }
        });
    }

    public List<SettingField> schema() {
        final List<SettingField> fields = new ArrayList<>();
        for (final SettingsSchema.Entry entry : SettingsSchema.ENTRIES) {
            fields.add(new SettingField(entry.key(), entry.type(),
                    this.config.defaultValue(entry.key()).orElse(""), entry.description(),
                    entry.options(), entry.min(), entry.max(),
                    entry.scope() == SettingsSchema.Scope.NETWORK, entry.editable(),
                    entry.scope() == SettingsSchema.Scope.RESTART));
        }
        return List.copyOf(fields);
    }

    public SettingsSnapshot settings() {
        final Map<String, Object> values = new LinkedHashMap<>();
        final Map<String, ValueOrigin> origins = new LinkedHashMap<>();
        final Map<String, String> overrides = this.config.overrides();
        for (final SettingsSchema.Entry entry : SettingsSchema.ENTRIES) {
            this.config.value(entry.key()).ifPresent(value -> values.put(entry.key(), value));
            origins.put(entry.key(), overrides.containsKey(entry.key()) ? ValueOrigin.OVERRIDE
                    : this.config.inFile(entry.key()) ? ValueOrigin.FILE : ValueOrigin.DEFAULT);
        }
        return new SettingsSnapshot(this.version.get(), values, origins);
    }

    public SortedMap<String, String> overrides() {
        return new TreeMap<>(this.config.overrides());
    }

    public String fileValue(final String key) {
        return this.config.fileValue(key).map(String::valueOf).orElse("");
    }

    public CompletableFuture<AdminResult<SettingsSnapshot>> update(
            final Map<String, Object> patch, final long expectedVersion, final Actor actor) {
        final Map<String, @Nullable String> changes = new HashMap<>();
        final List<FieldError> errors = new ArrayList<>();
        patch.forEach((key, value) -> {
            if (this.editable(key, errors)) {
                try {
                    changes.put(key, this.config.encode(value));
                } catch (final IllegalArgumentException e) {
                    errors.add(FieldError.of(key, FieldError.Code.INVALID, e.getMessage()));
                }
            }
        });
        return this.change(changes, errors, expectedVersion, actor);
    }

    public CompletableFuture<AdminResult<SettingsSnapshot>> reset(final Set<String> keys,
            final long expectedVersion, final Actor actor) {
        final Map<String, @Nullable String> changes = new HashMap<>();
        final List<FieldError> errors = new ArrayList<>();
        for (final String key : keys) {
            if (this.editable(key, errors)) {
                changes.put(key, null);
            }
        }
        return this.change(changes, errors, expectedVersion, actor);
    }

    private boolean editable(final String key, final List<FieldError> errors) {
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
        return true;
    }

    private CompletableFuture<AdminResult<SettingsSnapshot>> change(
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
                    final Map<String, String> next = new HashMap<>(this.config.overrides());
                    changes.forEach((key, value) -> {
                        if (value == null) {
                            next.remove(key);
                        } else {
                            next.put(key, value);
                        }
                    });
                    final List<FieldError> invalid = new ArrayList<>();
                    for (final ConfigIssue issue : this.config.checkOverrides(next)) {
                        invalid.add(FieldError.of(issue.path(), FieldError.Code.INVALID,
                                issue.message()));
                    }
                    if (!invalid.isEmpty()) {
                        return CompletableFuture.completedFuture(
                                new AdminResult.ValidationFailed<>(invalid));
                    }
                    return this.repository.changeSettings(changes, expectedVersion, checked,
                            this.clock.instant()).thenApply(written -> {
                                if (written.conflict()) {
                                    return new AdminResult.Conflict<>(written.version());
                                }
                                this.version.set(written.version());
                                this.config.applyOverrides(next);
                                this.sync.publish(new SyncPacket.EntityChangedPacket(
                                        SubjectType.SETTINGS));
                                return new AdminResult.Ok<>(this.settings());
                            });
                });
    }
}
