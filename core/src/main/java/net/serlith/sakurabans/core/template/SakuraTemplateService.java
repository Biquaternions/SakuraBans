package net.serlith.sakurabans.core.template;

import com.google.common.base.Preconditions;
import java.time.Instant;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import net.serlith.sakurabans.api.model.Actor;
import net.serlith.sakurabans.api.model.AddressVictim;
import net.serlith.sakurabans.api.model.CategoryScope;
import net.serlith.sakurabans.api.model.ConsoleOperator;
import net.serlith.sakurabans.api.model.GlobalScope;
import net.serlith.sakurabans.api.model.PlayerOperator;
import net.serlith.sakurabans.api.model.PlayerVictim;
import net.serlith.sakurabans.api.model.Scope;
import net.serlith.sakurabans.api.model.ServerScope;
import net.serlith.sakurabans.api.model.TemplateRef;
import net.serlith.sakurabans.api.model.Victim;
import net.serlith.sakurabans.api.template.LadderTemplate;
import net.serlith.sakurabans.api.template.TemplateInfo;
import net.serlith.sakurabans.api.template.TemplateService;
import net.serlith.sakurabans.api.template.TemplateStep;
import net.serlith.sakurabans.api.validation.FieldError.Code;
import net.serlith.sakurabans.api.validation.ValidationException;
import net.serlith.sakurabans.api.validation.Validator;
import net.serlith.sakurabans.core.configuration.ConfigIssue;
import net.serlith.sakurabans.core.configuration.ConfigService;
import net.serlith.sakurabans.core.configuration.SettingsLayers;
import net.serlith.sakurabans.core.configuration.TemplateFiles;
import net.serlith.sakurabans.core.model.AdminResult;
import net.serlith.sakurabans.core.model.ApiValues;
import net.serlith.sakurabans.core.model.StoredTemplate;
import net.serlith.sakurabans.core.model.SubjectType;
import net.serlith.sakurabans.core.model.TemplateBuilder;
import net.serlith.sakurabans.core.permission.Action;
import net.serlith.sakurabans.core.permission.AuthorizationService;
import net.serlith.sakurabans.core.permission.TemplateGuard;
import net.serlith.sakurabans.core.platform.OnlinePlayer;
import net.serlith.sakurabans.core.platform.Platform;
import net.serlith.sakurabans.core.scope.SakuraScopeService;
import net.serlith.sakurabans.core.storage.repository.LadderCount;
import net.serlith.sakurabans.core.storage.repository.MetaRepository;
import net.serlith.sakurabans.core.storage.repository.TemplateRepository;
import net.serlith.sakurabans.core.storage.repository.TemplateRepository.ResetCount;
import net.serlith.sakurabans.core.storage.repository.TemplateRepository.ResetSelection;
import net.serlith.sakurabans.core.sync.SyncPacket;
import net.serlith.sakurabans.core.sync.SyncService;
import net.serlith.sakurabans.core.util.Scopes;
import org.jspecify.annotations.Nullable;

public final class SakuraTemplateService implements TemplateService, TemplateGuard {

    static final String SEEDED = "templates.seeded";
    static final Actor SEED_ACTOR = Actor.of(ConsoleOperator.INSTANCE, "config");

    private final TemplateRepository repository;
    private final MetaRepository meta;
    private final TemplateFiles files;
    private final AuthorizationService authorization;
    private final SyncService sync;
    private final InstantSource clock;
    private final ConfigService config;
    private final SakuraScopeService scopes;
    private final Platform platform;
    private final ComponentLogger logger;
    private final AtomicReference<Catalog> catalog = new AtomicReference<>(Catalog.EMPTY);

    public SakuraTemplateService(final TemplateRepository repository, final MetaRepository meta,
            final TemplateFiles files, final AuthorizationService authorization,
            final SyncService sync, final InstantSource clock, final ConfigService config,
            final SakuraScopeService scopes, final Platform platform,
            final ComponentLogger logger) {
        this.repository = repository;
        this.meta = meta;
        this.files = files;
        this.authorization = authorization;
        this.sync = sync;
        this.clock = clock;
        this.config = config;
        this.scopes = scopes;
        this.platform = platform;
        this.logger = logger;
    }

    /**
     * Every definition in memory: the global ones by name, and each name's variants by scope.
     * Every instance keeps every variant: a punishment issued elsewhere is shown here from
     * the variant it came from.
     */
    private record Catalog(Map<String, StoredTemplate> global,
            Map<String, Map<Scope, StoredTemplate>> variants) {

        static final Catalog EMPTY = new Catalog(Map.of(), Map.of());
    }

    public CompletableFuture<Void> reload() {
        return this.repository.all().thenAccept(all -> {
            final Map<String, StoredTemplate> global = new TreeMap<>();
            final Map<String, Map<Scope, StoredTemplate>> variants = new TreeMap<>();
            for (final StoredTemplate template : all) {
                final Scope variant = template.variant().orElse(null);
                if (variant == null) {
                    global.put(template.name(), template);
                } else {
                    variants.computeIfAbsent(template.name(), ignored -> new TreeMap<>(
                            Comparator.comparing(Scopes::format))).put(variant, template);
                }
            }
            final Map<String, Map<Scope, StoredTemplate>> frozen = new TreeMap<>();
            variants.forEach((name, byScope) -> frozen.put(name,
                    Collections.unmodifiableMap(byScope)));
            this.catalog.set(new Catalog(Collections.unmodifiableMap(global),
                    Collections.unmodifiableMap(frozen)));
        });
    }

    public CompletableFuture<Void> start() {
        return this.meta.putIfAbsent(SEEDED, this.clock.instant().toString())
                .thenCompose(first -> {
                    if (!first) {
                        return this.reload();
                    }
                    final TemplateFiles.Read read = this.files.read();
                    read.errors().forEach(error -> this.logger.warn("Not imported: {}", error));
                    return this.merge(read.templates(), SEED_ACTOR).thenAccept(report ->
                            this.logger.info("Imported {} templates from {}",
                                    report.created(), TemplateFiles.FILE));
                });
    }

    public record MergeReport(int created, int updated, int unchanged,
            List<ConfigIssue> errors) {
    }

    public CompletableFuture<MergeReport> merge(final List<TemplateInfo> definitions,
            final Actor actor) {
        CompletableFuture<int[]> chain = CompletableFuture.completedFuture(new int[3]);
        final List<ConfigIssue> errors = new ArrayList<>();
        // Global definitions first: a variant needs its global template.
        final List<TemplateInfo> ordered = new ArrayList<>(definitions);
        ordered.sort(Comparator.comparing(definition -> definition.variant().isPresent()));
        for (final TemplateInfo definition : ordered) {
            final Scope variant = definition.variant().orElse(null);
            final String path = variant == null
                    ? "templates." + definition.name()
                    : "variants." + Scopes.format(variant) + "." + definition.name();
            chain = chain.thenCompose(counts -> {
                final StoredTemplate before = this.find(definition.name(), variant).orElse(null);
                return this.write(definition, null, actor, before == null).thenApply(outcome -> {
                    switch (outcome) {
                        case AdminResult.Ok<StoredTemplate> ok -> {
                            if (before == null) {
                                counts[0]++;
                            } else if (ok.value().version() != before.version()) {
                                counts[1]++;
                            } else {
                                counts[2]++;
                            }
                        }
                        case AdminResult.ValidationFailed<StoredTemplate> failed ->
                                failed.errors().forEach(error -> errors.add(ConfigIssue.error(
                                        TemplateFiles.FILE, path + "." + error.path(),
                                        error.message())));
                        default -> errors.add(ConfigIssue.error(TemplateFiles.FILE, path,
                                "changed meanwhile; not imported"));
                    }
                    return counts;
                });
            });
        }
        return chain.thenApply(counts -> new MergeReport(counts[0], counts[1], counts[2],
                List.copyOf(errors)));
    }

    @Override
    public List<TemplateInfo> templates() {
        return this.catalog.get().global().values().stream().map(StoredTemplate::template)
                .toList();
    }

    @Override
    public Optional<TemplateInfo> template(final String name) {
        return this.find(name).map(StoredTemplate::template);
    }

    @Override
    public Optional<TemplateInfo> template(final String name, final Scope scope) {
        Preconditions.checkNotNull(scope, "scope");
        return this.find(name).flatMap(global -> this.firstOf(global.name(),
                this.layers(ApiValues.scope(scope)))).map(StoredTemplate::template);
    }

    /** The global definition. */
    public Optional<StoredTemplate> find(final String name) {
        Preconditions.checkNotNull(name, "name");
        return Validator.normalizeIdentifier(name).map(this.catalog.get().global()::get);
    }

    /** One definition exactly: the global one for null or {@code global}, else that variant. */
    public Optional<StoredTemplate> find(final String name, final @Nullable Scope variant) {
        if (variant == null || variant instanceof GlobalScope) {
            return this.find(name);
        }
        Preconditions.checkNotNull(name, "name");
        return Validator.normalizeIdentifier(name)
                .map(normalized -> this.catalog.get().variants()
                        .getOrDefault(normalized, Map.of()).get(variant));
    }

    /** A name's variants, by scope text. */
    public Map<Scope, StoredTemplate> variants(final String name) {
        return Validator.normalizeIdentifier(name)
                .map(normalized -> this.catalog.get().variants()
                        .getOrDefault(normalized, Map.of()))
                .orElse(Map.of());
    }

    /** Every definition: by name, the global one first, then its variants by scope text. */
    public List<StoredTemplate> definitions() {
        final Catalog current = this.catalog.get();
        final List<StoredTemplate> all = new ArrayList<>();
        current.global().forEach((name, global) -> {
            all.add(global);
            all.addAll(current.variants().getOrDefault(name, Map.of()).values());
        });
        return List.copyOf(all);
    }

    /** The definitions written for one scope: the global ones, or that scope's variants. */
    public List<StoredTemplate> definitionsAt(final Scope scope) {
        if (scope instanceof GlobalScope) {
            return List.copyOf(this.catalog.get().global().values());
        }
        return this.definitions().stream()
                .filter(template -> template.variant().filter(scope::equals).isPresent())
                .toList();
    }

    /**
     * The definition a use gets (rule in {@link TemplateService}): {@code explicit} is the
     * scope the request names (null: none); without one, this server on a backend, the
     * operator's server on a proxy, else global.
     */
    public Optional<StoredTemplate> resolve(final String name,
            final @Nullable Scope explicit, final Actor actor) {
        final StoredTemplate global = this.find(name).orElse(null);
        if (global == null) {
            return Optional.empty();
        }
        return this.firstOf(global.name(), this.layers(this.context(explicit, actor)));
    }

    /** Where a use without its own scope picks its variant from; null: this server's layers. */
    private @Nullable Scope context(final @Nullable Scope explicit, final Actor actor) {
        if (explicit != null) {
            return explicit;
        }
        if (!this.scopes.proxy()) {
            return null;
        }
        return actor.operator() instanceof PlayerOperator player
                ? this.platform.player(player.uuid())
                        .flatMap(OnlinePlayer::currentServer)
                        .flatMap(Validator::normalizeIdentifier)
                        .<Scope>map(ServerScope::of)
                        .orElse(GlobalScope.INSTANCE)
                : GlobalScope.INSTANCE;
    }

    /**
     * The scopes a definition is looked for in, the winning one first, {@code global} last.
     * Null: this server's own layers (as for settings and messages).
     */
    private List<Scope> layers(final @Nullable Scope where) {
        return switch (where) {
            case null -> this.config.layers();
            case GlobalScope global -> List.of(GlobalScope.INSTANCE);
            case CategoryScope category -> List.of(category, GlobalScope.INSTANCE);
            case ServerScope server -> this.scopes.thisServer().filter(server::equals)
                    .isPresent() && !this.scopes.proxy()
                    ? this.config.layers()
                    : SettingsLayers.scopes(server.server(), List.of(),
                            this.config.settings().scopes().categories());
        };
    }

    private Optional<StoredTemplate> firstOf(final String name, final List<Scope> layers) {
        for (final Scope layer : layers) {
            final Optional<StoredTemplate> found = this.find(name, layer);
            if (found.isPresent()) {
                return found;
            }
        }
        return this.find(name);
    }

    @Override
    public Optional<String> guarding(final TemplateRef ref) {
        final boolean guarded = this.definition(ref).map(StoredTemplate::template)
                .filter(TemplateInfo::permissionRequired).isPresent()
                || this.find(ref.name()).map(StoredTemplate::template)
                        .filter(TemplateInfo::permissionRequired).isPresent();
        return guarded ? Validator.normalizeIdentifier(ref.name()) : Optional.empty();
    }

    @Override
    public Set<String> guardedNames() {
        final Catalog catalog = this.catalog.get();
        final Set<String> names = new TreeSet<>();
        catalog.global().forEach((name, stored) -> {
            if (stored.template().permissionRequired()) {
                names.add(name);
            }
        });
        catalog.variants().forEach((name, variants) -> {
            if (variants.values().stream().anyMatch(
                    stored -> stored.template().permissionRequired())) {
                names.add(name);
            }
        });
        return Set.copyOf(names);
    }

    /**
     * The definition a punishment or use was made from: its variant, else (deleted) the global
     * definition, else empty (deleted too).
     */
    public Optional<StoredTemplate> definition(final TemplateRef ref) {
        return this.find(ref.name(), ref.variant().orElse(null)).or(() -> this.find(ref.name()));
    }

    @Override
    public CompletableFuture<OptionalInt> nextStep(final String ladder, final Victim victim) {
        return this.nextStep(ladder, victim, null);
    }

    @Override
    public CompletableFuture<OptionalInt> nextStep(final String ladder, final Victim victim,
            final @Nullable Scope scope) {
        Preconditions.checkNotNull(ladder, "ladder");
        final Optional<StoredTemplate> definition;
        try {
            // An explicit scope picks the variant as /punish --scope does; null: global.
            definition = scope == null ? this.find(ladder)
                    : this.find(ladder).flatMap(global -> this.firstOf(global.name(),
                            this.layers(ApiValues.scope(scope))));
        } catch (final RuntimeException e) {
            return CompletableFuture.failedFuture(e);
        }
        if (!(definition.map(StoredTemplate::template).orElse(null)
                instanceof LadderTemplate found)) {
            return CompletableFuture.completedFuture(OptionalInt.empty());
        }
        final Victim checked;
        try {
            checked = ApiValues.victim(victim);
        } catch (final RuntimeException e) {
            return CompletableFuture.failedFuture(e);
        }
        return this.step(found, checked).thenApply(OptionalInt::of);
    }

    /**
     * The victim's step on the ladder: its points are the earlier uses of every template
     * sharing a group with it, each with its weight (Presets too).
     */
    public CompletableFuture<Integer> step(final LadderTemplate template, final Victim victim) {
        return this.step(victim, this.counting(template));
    }

    public CompletableFuture<Integer> step(final Victim victim, final LadderCount count) {
        return this.repository.step(victim, count);
    }

    /**
     * How the ladder counts a step now: the same count before an apply and in the
     * transaction that stores its use.
     */
    public LadderCount counting(final LadderTemplate template) {
        final Instant now = this.clock.instant();
        // Points (earlier uses, weighted) give the step, capped at the last one.
        final int last = template.steps().size() - 1;
        return new LadderCount(this.weights(template), template.addressShared(),
                template.expiry().map(now::minus).orElse(null), now,
                points -> Math.clamp(points, 0, last));
    }

    /** Groups and weights come from the global definitions only. */
    Map<String, Integer> weights(final TemplateInfo template) {
        final Map<String, Integer> weights = new HashMap<>();
        final TemplateInfo global = template.variant().isEmpty() ? template
                : this.find(template.name()).map(StoredTemplate::template).orElse(template);
        final Map<String, Integer> groups = global.groups();
        if (groups.isEmpty()) {
            weights.put(template.name(), 1);
            return weights;
        }
        final Map<String, TemplateInfo> all = new HashMap<>();
        this.catalog.get().global().forEach((name, stored) -> all.put(name, stored.template()));
        all.put(global.name(), global);
        for (final TemplateInfo other : all.values()) {
            for (final Map.Entry<String, Integer> group : other.groups().entrySet()) {
                if (groups.containsKey(group.getKey())) {
                    weights.merge(other.name(), group.getValue(), Math::max);
                }
            }
        }
        return weights;
    }

    /**
     * What {@link #resetUses} would delete now. {@code target}: a player or an address;
     * {@code template}: one template, or null for all.
     */
    public CompletableFuture<AdminResult<ResetCount>> resetCount(final Victim target,
            final @Nullable String template, final Actor actor) {
        return this.reset(target, actor, checked -> this.repository.resetCount(target,
                this.resetSelections(template), this.clock.instant()));
    }

    /**
     * Forgets the uses each template concerned would count for the target, so its ladders
     * start over: for a ladder, the uses of it and its group mates (weights of the
     * global definitions), on the target's alts and addresses too when the ladder (its global
     * definition or a variant) is {@code address-shared}; for a preset or a name without a
     * template, the target's own uses. Without a template: the target's own uses of every
     * template, and what each {@code address-shared} ladder counts for it. Every such use goes,
     * also those outside the expiry window or of a voided punishment, which count no more.
     */
    public CompletableFuture<AdminResult<ResetCount>> resetUses(final Victim target,
            final @Nullable String template, final Actor actor) {
        return this.reset(target, actor, checked -> this.repository.resetUses(target,
                template, this.resetSelections(template), checked, this.clock.instant()));
    }

    private CompletableFuture<AdminResult<ResetCount>> reset(final Victim target,
            final Actor actor,
            final Function<Actor, CompletableFuture<ResetCount>> work) {
        final Actor checked;
        try {
            checked = ApiValues.actor(actor);
            Preconditions.checkArgument(target instanceof PlayerVictim
                    || target instanceof AddressVictim, "a reset names a player or an address");
        } catch (final RuntimeException e) {
            return CompletableFuture.failedFuture(e);
        }
        return this.authorization.check(checked, new Action.Manage(Action.Manage.Area.TEMPLATES))
                .thenCompose(decision -> decision.allowed()
                        ? work.apply(checked).thenApply(AdminResult.Ok::new)
                        : CompletableFuture.completedFuture(new AdminResult.Forbidden<>()));
    }

    private List<ResetSelection> resetSelections(final @Nullable String template) {
        final List<ResetSelection> selections = new ArrayList<>();
        if (template != null) {
            final String name = Validator.normalizeIdentifier(template).orElse(template);
            if (this.find(name).map(StoredTemplate::template).orElse(null)
                    instanceof LadderTemplate ladder) {
                selections.add(new ResetSelection(this.weights(ladder).keySet(),
                        this.shared(ladder)));
            } else {
                selections.add(new ResetSelection(Set.of(name), false));
            }
            return selections;
        }
        selections.add(new ResetSelection(null, false));
        for (final StoredTemplate global : this.catalog.get().global().values()) {
            if (global.template() instanceof LadderTemplate ladder && this.shared(ladder)) {
                selections.add(new ResetSelection(this.weights(ladder).keySet(), true));
            }
        }
        return selections;
    }

    /** Whether any definition of the ladder counts across alts and addresses. */
    private boolean shared(final LadderTemplate global) {
        return global.addressShared() || this.variants(global.name()).values().stream()
                .anyMatch(variant -> variant.template() instanceof LadderTemplate ladder
                        && ladder.addressShared());
    }

    public CompletableFuture<AdminResult<StoredTemplate>> create(
            final TemplateInfo definition, final Actor actor) {
        return this.authorized(actor, () -> this.write(definition, null, actor, true));
    }

    public CompletableFuture<AdminResult<StoredTemplate>> update(
            final TemplateInfo definition, final long expectedVersion, final Actor actor) {
        return this.authorized(actor, () -> this.write(definition, expectedVersion, actor,
                false));
    }

    public CompletableFuture<AdminResult<StoredTemplate>> reorderSteps(
            final @Nullable Scope variant, final String name, final List<Integer> order,
            final long expectedVersion, final Actor actor) {
        return this.authorized(actor, () -> {
            final StoredTemplate template = this.find(name, variant).orElse(null);
            if (template == null) {
                return CompletableFuture.completedFuture(new AdminResult.NotFound<>());
            }
            if (!(template.template() instanceof LadderTemplate ladder)) {
                return CompletableFuture.completedFuture(AdminResult.invalid("steps",
                        Code.NOT_ALLOWED, "only a ladder has steps"));
            }
            final int size = ladder.steps().size();
            if (order.size() != size || !new HashSet<>(order).equals(range(size))) {
                return CompletableFuture.completedFuture(AdminResult.invalid("order",
                        Code.INVALID, "must list each step index 0-" + (size - 1) + " once"));
            }
            final List<TemplateStep> steps = order.stream()
                    .map(ladder.steps()::get)
                    .toList();
            return this.write(TemplateBuilder.from(ladder).steps(steps).build(),
                    expectedVersion, actor, false);
        });
    }

    /**
     * Deletes one definition; the global one takes its variants with it
     * ({@link #deleteWithVariants} also says which).
     */
    public CompletableFuture<AdminResult<StoredTemplate>> delete(
            final @Nullable Scope variant, final String name, final long expectedVersion,
            final Actor actor) {
        return this.deleteWithVariants(variant, name, expectedVersion, actor)
                .thenApply(result -> switch (result) {
                    case AdminResult.Ok<Deletion> ok -> new AdminResult.Ok<>(ok.value().value());
                    case AdminResult.Conflict<Deletion> conflict ->
                            new AdminResult.Conflict<>(conflict.currentVersion());
                    case AdminResult.NotFound<Deletion> notFound -> new AdminResult.NotFound<>();
                    case AdminResult.Forbidden<Deletion> forbidden ->
                            new AdminResult.Forbidden<>();
                    case AdminResult.ValidationFailed<Deletion> failed ->
                            new AdminResult.ValidationFailed<>(failed.errors());
                });
    }

    /** A deleted definition, and the variants that went with it (global only). */
    public record Deletion(StoredTemplate value, List<Scope> variants) {
    }

    public CompletableFuture<AdminResult<Deletion>> deleteWithVariants(
            final @Nullable Scope variant, final String name, final long expectedVersion,
            final Actor actor) {
        final Actor checked;
        try {
            checked = ApiValues.actor(actor);
        } catch (final RuntimeException e) {
            return CompletableFuture.failedFuture(e);
        }
        return this.authorization.check(checked, new Action.Manage(Action.Manage.Area.TEMPLATES))
                .thenCompose(decision -> {
                    if (!decision.allowed()) {
                        return CompletableFuture.completedFuture(new AdminResult.Forbidden<>());
                    }
                    final String normalized = Validator.normalizeIdentifier(name).orElse(null);
                    if (normalized == null) {
                        return CompletableFuture.completedFuture(new AdminResult.NotFound<>());
                    }
                    return this.repository.delete(variant, normalized, expectedVersion,
                            checked, this.clock.instant()).thenCompose(write -> {
                                if (write instanceof TemplateRepository.Write.Deleted deleted) {
                                    return this.reload().thenApply(ignored -> {
                                        this.sync.publish(new SyncPacket.EntityChangedPacket(
                                                SubjectType.TEMPLATE));
                                        return (AdminResult<Deletion>) new AdminResult.Ok<>(
                                                new Deletion(deleted.value(),
                                                        deleted.variants()));
                                    });
                                }
                                return this.written(write).thenApply(result -> switch (result) {
                                    case AdminResult.Conflict<StoredTemplate> conflict ->
                                            new AdminResult.Conflict<>(
                                                    conflict.currentVersion());
                                    case AdminResult.ValidationFailed<StoredTemplate> f ->
                                            new AdminResult.ValidationFailed<>(f.errors());
                                    default -> new AdminResult.NotFound<>();
                                });
                            });
                });
    }

    private CompletableFuture<AdminResult<StoredTemplate>> write(
            final TemplateInfo given, final @Nullable Long expectedVersion,
            final Actor actor, final boolean create) {
        final TemplateInfo definition;
        try {
            // Built again, so a definition made without the builder is checked too.
            definition = TemplateBuilder.from(given).build();
        } catch (final ValidationException e) {
            return CompletableFuture.completedFuture(
                    new AdminResult.ValidationFailed<>(e.errors()));
        }
        final Instant now = this.clock.instant();
        final Actor checked = ApiValues.actor(actor);
        return (create
                ? this.repository.create(definition, checked, now)
                : this.repository.update(definition, expectedVersion, checked, now))
                .thenCompose(this::written);
    }

    private CompletableFuture<AdminResult<StoredTemplate>> written(
            final TemplateRepository.Write write) {
        return switch (write) {
            case TemplateRepository.Write.Done done -> this.reload().thenApply(ignored -> {
                final StoredTemplate template = done.value();
                this.sync.publish(new SyncPacket.EntityChangedPacket(SubjectType.TEMPLATE));
                return new AdminResult.Ok<>(template);
            });
            case TemplateRepository.Write.Deleted deleted -> this.reload().thenApply(ignored -> {
                this.sync.publish(new SyncPacket.EntityChangedPacket(SubjectType.TEMPLATE));
                return new AdminResult.Ok<>(deleted.value());
            });
            case TemplateRepository.Write.NoGlobal noGlobal ->
                    CompletableFuture.completedFuture(AdminResult.invalid("name",
                            Code.NOT_ALLOWED, "no global template has this name; a variant"
                                    + " needs one"));
            case TemplateRepository.Write.KindMismatch mismatch ->
                    CompletableFuture.completedFuture(AdminResult.invalid("type",
                            Code.NOT_ALLOWED, mismatch.variants().isEmpty()
                                    ? "a variant has its global template's type: "
                                            + mismatch.expected().key()
                                    : "its variants (" + String.join(", ", mismatch.variants()
                                            .stream().map(Scopes::format).toList())
                                            + ") are of type " + mismatch.expected().key()
                                            + "; change or delete them first"));
            case TemplateRepository.Write.Unchanged unchanged ->
                    CompletableFuture.completedFuture(new AdminResult.Ok<>(
                            unchanged.value()));
            case TemplateRepository.Write.Taken taken ->
                    CompletableFuture.completedFuture(AdminResult.invalid("name",
                            Code.NOT_ALLOWED, taken.existing().variant().isEmpty()
                                    ? "a template is called '" + taken.existing().name()
                                            + "' already"
                                    : "'" + taken.existing().name() + "' has a variant for "
                                            + Scopes.format(taken.existing().variant()
                                                    .orElseThrow()) + " already"));
            case TemplateRepository.Write.NotFound notFound ->
                    CompletableFuture.completedFuture(new AdminResult.NotFound<>());
            case TemplateRepository.Write.Conflict conflict ->
                    CompletableFuture.completedFuture(new AdminResult.Conflict<>(
                            conflict.currentVersion()));
        };
    }

    private CompletableFuture<AdminResult<StoredTemplate>> authorized(final Actor actor,
            final Supplier<CompletableFuture<AdminResult<StoredTemplate>>> work) {
        final Actor checked;
        try {
            checked = ApiValues.actor(actor);
        } catch (final RuntimeException e) {
            return CompletableFuture.failedFuture(e);
        }
        return this.authorization.check(checked, new Action.Manage(Action.Manage.Area.TEMPLATES))
                .thenCompose(decision -> decision.allowed()
                        ? work.get()
                        : CompletableFuture.completedFuture(
                                new AdminResult.Forbidden<>()));
    }

    private static Set<Integer> range(final int size) {
        final Set<Integer> all = new HashSet<>();
        for (int i = 0; i < size; i++) {
            all.add(i);
        }
        return all;
    }
}
