package net.serlith.sakurabans.core.template;

import com.google.common.base.Preconditions;
import java.time.Instant;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import net.serlith.sakurabans.api.model.Actor;
import net.serlith.sakurabans.api.model.ConsoleOperator;
import net.serlith.sakurabans.api.model.Victim;
import net.serlith.sakurabans.api.template.TemplateInfo;
import net.serlith.sakurabans.api.template.TemplateService;
import net.serlith.sakurabans.api.template.TemplateStep;
import net.serlith.sakurabans.api.validation.FieldError.Code;
import net.serlith.sakurabans.api.validation.ValidationException;
import net.serlith.sakurabans.api.validation.Validator;
import net.serlith.sakurabans.core.configuration.ConfigIssue;
import net.serlith.sakurabans.core.configuration.TemplateFiles;
import net.serlith.sakurabans.core.model.AdminResult;
import net.serlith.sakurabans.core.model.ApiValues;
import net.serlith.sakurabans.core.model.SakuraTemplateInfo;
import net.serlith.sakurabans.core.model.SubjectType;
import net.serlith.sakurabans.core.model.TemplateDefinition;
import net.serlith.sakurabans.core.permission.Action;
import net.serlith.sakurabans.core.permission.AuthorizationService;
import net.serlith.sakurabans.core.punish.TemplateResolver;
import net.serlith.sakurabans.core.storage.repository.MetaRepository;
import net.serlith.sakurabans.core.storage.repository.TemplateRepository;
import net.serlith.sakurabans.core.sync.SyncPacket;
import net.serlith.sakurabans.core.sync.SyncService;
import org.jspecify.annotations.Nullable;

public final class SakuraTemplateService implements TemplateService, TemplateResolver {

    static final String SEEDED = "templates.seeded";
    static final Actor SEED_ACTOR = Actor.of(ConsoleOperator.INSTANCE, "config");

    private final TemplateRepository repository;
    private final MetaRepository meta;
    private final TemplateFiles files;
    private final AuthorizationService authorization;
    private final SyncService sync;
    private final InstantSource clock;
    private final ComponentLogger logger;
    private final AtomicReference<Map<String, SakuraTemplateInfo>> templates =
            new AtomicReference<>(Map.of());

    public SakuraTemplateService(final TemplateRepository repository, final MetaRepository meta,
            final TemplateFiles files, final AuthorizationService authorization,
            final SyncService sync, final InstantSource clock,
            final ComponentLogger logger) {
        this.repository = repository;
        this.meta = meta;
        this.files = files;
        this.authorization = authorization;
        this.sync = sync;
        this.clock = clock;
        this.logger = logger;
    }

    public CompletableFuture<Void> reload() {
        return this.repository.all().thenAccept(all -> {
            final Map<String, SakuraTemplateInfo> byName = new TreeMap<>();
            all.forEach(template -> byName.put(template.name(), template));
            this.templates.set(Map.copyOf(byName));
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

    public CompletableFuture<MergeReport> merge(final List<TemplateDefinition> definitions,
            final Actor actor) {
        CompletableFuture<int[]> chain = CompletableFuture.completedFuture(new int[3]);
        final List<ConfigIssue> errors = new ArrayList<>();
        for (final TemplateDefinition definition : definitions) {
            chain = chain.thenCompose(counts -> {
                final SakuraTemplateInfo before = this.templates.get().get(definition.name());
                return this.write(definition, null, actor, before == null).thenApply(outcome -> {
                    switch (outcome) {
                        case AdminResult.Ok<SakuraTemplateInfo> ok -> {
                            if (before == null) {
                                counts[0]++;
                            } else if (ok.value().version() != before.version()) {
                                counts[1]++;
                            } else {
                                counts[2]++;
                            }
                        }
                        case AdminResult.ValidationFailed<SakuraTemplateInfo> failed ->
                                failed.errors().forEach(error -> errors.add(ConfigIssue.error(
                                        TemplateFiles.FILE, "templates." + definition.name()
                                                + "." + error.path(), error.message())));
                        default -> errors.add(ConfigIssue.error(TemplateFiles.FILE, "templates."
                                + definition.name(), "changed meanwhile; not imported"));
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
        return List.copyOf(this.templates.get().values());
    }

    @Override
    public Optional<TemplateInfo> template(final String name) {
        return this.find(name).map(TemplateInfo.class::cast);
    }

    public Optional<SakuraTemplateInfo> find(final String name) {
        Preconditions.checkNotNull(name, "name");
        return Validator.normalizeIdentifier(name).map(this.templates.get()::get);
    }

    @Override
    public CompletableFuture<Optional<Resolution>> resolve(final String name,
            final Victim victim) {
        final SakuraTemplateInfo template = this.find(name).orElse(null);
        if (template == null) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        return this.step(template, victim).thenApply(step -> Optional.of(
                new Resolution(template, template.ladder().get(step).duration().orElse(null),
                        step)));
    }

    CompletableFuture<Integer> step(final SakuraTemplateInfo template, final Victim victim) {
        final Map<String, Integer> weights = this.weights(template);
        final Instant now = this.clock.instant();
        final Instant since = template.ladderExpiry().map(now::minus).orElse(null);
        return this.repository.history(weights.keySet(), victim, template.addressShared(), since,
                now).thenApply(counts -> {
                    long points = 0;
                    for (final Map.Entry<String, Long> count : counts.entrySet()) {
                        points += count.getValue() * weights.getOrDefault(count.getKey(), 0);
                    }
                    return template.stepFor(points);
                });
    }

    Map<String, Integer> weights(final SakuraTemplateInfo template) {
        final Map<String, Integer> weights = new HashMap<>();
        if (template.groups().isEmpty()) {
            weights.put(template.name(), 1);
            return weights;
        }
        final Map<String, SakuraTemplateInfo> all = new HashMap<>(this.templates.get());
        all.put(template.name(), template);
        for (final SakuraTemplateInfo other : all.values()) {
            for (final Map.Entry<String, Integer> group : other.groups().entrySet()) {
                if (template.groups().containsKey(group.getKey())) {
                    weights.merge(other.name(), group.getValue(), Math::max);
                }
            }
        }
        return weights;
    }

    public CompletableFuture<AdminResult<SakuraTemplateInfo>> create(
            final TemplateDefinition definition, final Actor actor) {
        return this.authorized(actor, () -> this.write(definition, null, actor, true));
    }

    public CompletableFuture<AdminResult<SakuraTemplateInfo>> update(
            final TemplateDefinition definition, final long expectedVersion, final Actor actor) {
        return this.authorized(actor, () -> this.write(definition, expectedVersion, actor,
                false));
    }

    public CompletableFuture<AdminResult<SakuraTemplateInfo>> reorderSteps(final String name,
            final List<Integer> order, final long expectedVersion, final Actor actor) {
        return this.authorized(actor, () -> {
            final SakuraTemplateInfo template = this.find(name).orElse(null);
            if (template == null) {
                return CompletableFuture.completedFuture(new AdminResult.NotFound<>());
            }
            final int size = template.ladder().size();
            if (order.size() != size || !new HashSet<>(order).equals(range(size))) {
                return CompletableFuture.completedFuture(AdminResult.invalid("order",
                        Code.INVALID, "must list each step index 0-" + (size - 1) + " once"));
            }
            final List<TemplateStep> ladder = order.stream()
                    .map(template.ladder()::get)
                    .toList();
            return this.write(TemplateDefinition.builder(template).ladder(ladder).build(),
                    expectedVersion, actor, false);
        });
    }

    public CompletableFuture<AdminResult<SakuraTemplateInfo>> delete(final String name,
            final long expectedVersion, final Actor actor) {
        return this.authorized(actor, () -> {
            final String normalized = Validator.normalizeIdentifier(name).orElse(null);
            if (normalized == null) {
                return CompletableFuture.completedFuture(new AdminResult.NotFound<>());
            }
            return this.repository.delete(normalized, expectedVersion, ApiValues.actor(actor),
                    this.clock.instant()).thenCompose(this::written);
        });
    }

    private CompletableFuture<AdminResult<SakuraTemplateInfo>> write(
            final TemplateDefinition given, final @Nullable Long expectedVersion,
            final Actor actor, final boolean create) {
        final TemplateDefinition definition;
        try {
            definition = given.toBuilder()
                    .ladder(given.ladder().stream()
                            .map(step -> TemplateStep.of(step.duration().orElse(null),
                                    step.actions()))
                            .toList())
                    .build();
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

    private CompletableFuture<AdminResult<SakuraTemplateInfo>> written(
            final TemplateRepository.Write write) {
        return switch (write) {
            case TemplateRepository.Write.Done done -> this.reload().thenApply(ignored -> {
                final SakuraTemplateInfo template = done.value();
                this.sync.publish(new SyncPacket.EntityChangedPacket(SubjectType.TEMPLATE));
                return new AdminResult.Ok<>(template);
            });
            case TemplateRepository.Write.Unchanged unchanged ->
                    CompletableFuture.completedFuture(new AdminResult.Ok<>(
                            unchanged.value()));
            case TemplateRepository.Write.Taken taken ->
                    CompletableFuture.completedFuture(AdminResult.invalid("name",
                            Code.NOT_ALLOWED, "a template is called '" + taken.existing().name()
                                    + "' already"));
            case TemplateRepository.Write.NotFound notFound ->
                    CompletableFuture.completedFuture(new AdminResult.NotFound<>());
            case TemplateRepository.Write.Conflict conflict ->
                    CompletableFuture.completedFuture(new AdminResult.Conflict<>(
                            conflict.currentVersion()));
        };
    }

    private CompletableFuture<AdminResult<SakuraTemplateInfo>> authorized(final Actor actor,
            final Supplier<CompletableFuture<AdminResult<SakuraTemplateInfo>>> work) {
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
