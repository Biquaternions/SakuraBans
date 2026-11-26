package net.serlith.sakurabans.core.command.module;

import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.serlith.sakurabans.api.model.AddressVictim;
import net.serlith.sakurabans.api.model.CategoryScope;
import net.serlith.sakurabans.api.model.ConsoleOperator;
import net.serlith.sakurabans.api.model.GlobalScope;
import net.serlith.sakurabans.api.model.Operator;
import net.serlith.sakurabans.api.model.PlayerOperator;
import net.serlith.sakurabans.api.model.PlayerVictim;
import net.serlith.sakurabans.api.model.Punishment;
import net.serlith.sakurabans.api.model.Scope;
import net.serlith.sakurabans.api.model.ServerScope;
import net.serlith.sakurabans.api.model.Victim;
import net.serlith.sakurabans.api.punish.ExpungeResult;
import net.serlith.sakurabans.api.punish.PunishmentQuery;
import net.serlith.sakurabans.api.punish.RollbackRequest;
import net.serlith.sakurabans.api.punish.RollbackResult;
import net.serlith.sakurabans.core.command.CommandActor;
import net.serlith.sakurabans.core.command.CommandModule;
import net.serlith.sakurabans.core.command.CommandRegistrar;
import net.serlith.sakurabans.core.command.CommandRunner;
import net.serlith.sakurabans.core.command.ConfirmationTracker;
import net.serlith.sakurabans.core.command.PunishFlags;
import net.serlith.sakurabans.core.command.Replies;
import net.serlith.sakurabans.core.command.SakuraCommands;
import net.serlith.sakurabans.core.command.parser.DurationParser;
import net.serlith.sakurabans.core.command.parser.TargetParser;
import net.serlith.sakurabans.core.configuration.ConfigIssue;
import net.serlith.sakurabans.core.configuration.ConfigService;
import net.serlith.sakurabans.core.configuration.ReloadResult;
import net.serlith.sakurabans.core.enforce.LockdownService;
import net.serlith.sakurabans.core.message.Formats;
import net.serlith.sakurabans.core.message.MessageKey;
import net.serlith.sakurabans.core.message.MessageService;
import net.serlith.sakurabans.core.message.Placeholders;
import net.serlith.sakurabans.core.model.Lockdown;
import net.serlith.sakurabans.core.model.ResolvedTarget;
import net.serlith.sakurabans.core.model.TargetInput;
import net.serlith.sakurabans.core.permission.Permissions;
import net.serlith.sakurabans.core.platform.Platform;
import net.serlith.sakurabans.core.player.TargetResolver;
import net.serlith.sakurabans.core.punish.SakuraPunishmentService;
import net.serlith.sakurabans.core.scope.SakuraScopeService;
import net.serlith.sakurabans.core.storage.repository.PunishmentRepository;
import net.serlith.sakurabans.core.util.BuildConstants;
import org.incendo.cloud.Command;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.parser.flag.CommandFlag;
import org.incendo.cloud.parser.flag.FlagContext;
import org.incendo.cloud.parser.standard.StringParser;
import org.jspecify.annotations.Nullable;

public final class AdminCommands implements CommandModule {

    private static final String CONFIRM = "confirm";
    private static final String OLDER_THAN = "older-than";

    private final CommandRegistrar registrar;
    private final CommandRunner runner;
    private final Replies replies;
    private final Placeholders placeholders;
    private final Formats formats;
    private final PunishFlags flags;
    private final TargetParser targetParser;
    private final TargetResolver targets;
    private final SakuraPunishmentService punishments;
    private final PunishmentRepository repository;
    private final LockdownService lockdowns;
    private final SakuraScopeService scopes;
    private final ConfigService config;
    private final MessageService messages;
    private final Platform platform;
    private final ConfirmationTracker confirmations;
    private final Executor worker;
    private final InstantSource clock;

    public AdminCommands(final CommandRegistrar registrar, final CommandRunner runner,
            final Replies replies, final Placeholders placeholders, final Formats formats,
            final PunishFlags flags, final TargetParser targetParser,
            final TargetResolver targets, final SakuraPunishmentService punishments,
            final PunishmentRepository repository, final LockdownService lockdowns,
            final SakuraScopeService scopes, final ConfigService config,
            final MessageService messages, final Platform platform,
            final ConfirmationTracker confirmations,
            final Executor worker, final InstantSource clock) {
        this.registrar = registrar;
        this.runner = runner;
        this.replies = replies;
        this.placeholders = placeholders;
        this.formats = formats;
        this.flags = flags;
        this.targetParser = targetParser;
        this.targets = targets;
        this.punishments = punishments;
        this.repository = repository;
        this.lockdowns = lockdowns;
        this.scopes = scopes;
        this.config = config;
        this.messages = messages;
        this.platform = platform;
        this.confirmations = confirmations;
        this.worker = worker;
        this.clock = clock;
    }

    @Override
    public void register(final CommandManager<CommandActor> manager) {
        this.registrar.builder(manager, "lockdown", MessageKey.COMMAND_DESCRIPTION_LOCKDOWN)
                .ifPresent(root -> {
                    final var lockdown = root.permission(Permissions.ADMIN_LOCKDOWN);
                    manager.command(this.scoped(lockdown)
                            .flag(this.flags.duration())
                            .flag(this.flags.reason())
                            .futureHandler(context -> this.runner.run(context.sender(),
                                    () -> this.lockdown(context))));
                    manager.command(this.scoped(lockdown.literal("end"))
                            .futureHandler(context -> this.runner.run(context.sender(),
                                    () -> this.endLockdown(context))));
                    manager.command(lockdown.literal("status")
                            .handler(context -> this.status(context.sender())));
                });
        this.registrar.builder(manager, "staffrollback",
                        MessageKey.COMMAND_DESCRIPTION_STAFFROLLBACK)
                .ifPresent(root -> manager.command(root
                        .permission(Permissions.ADMIN_STAFFROLLBACK)
                        .required("staff", StringParser.stringParser())
                        .required("since", DurationParser.descriptor())
                        .flag(CommandFlag.builder(CONFIRM).build())
                        .flag(this.flags.reason())
                        .futureHandler(context -> this.runner.run(context.sender(),
                                () -> this.rollback(context)))));
        this.registrar.builder(manager, "prunehistory",
                        MessageKey.COMMAND_DESCRIPTION_PRUNEHISTORY)
                .ifPresent(root -> manager.command(root
                        .permission(Permissions.ADMIN_PRUNEHISTORY)
                        .required("target", this.targetParser.descriptor())
                        .flag(CommandFlag.<CommandActor>builder(OLDER_THAN)
                                .withComponent(DurationParser.descriptor()).build())
                        .flag(CommandFlag.builder(CONFIRM).build())
                        .futureHandler(context -> this.runner.run(context.sender(),
                                () -> this.prune(context)))));
        this.registrar.builder(manager, SakuraCommands.ROOT,
                        MessageKey.COMMAND_DESCRIPTION_STATUS)
                .ifPresent(root -> manager.command(root.literal("status")
                        .permission(Permissions.ADMIN_STATUS)
                        .futureHandler(context -> this.runner.run(context.sender(),
                                () -> this.serverStatus(context.sender())))));
        this.registrar.builder(manager, SakuraCommands.ROOT,
                        MessageKey.COMMAND_DESCRIPTION_RELOAD)
                .ifPresent(root -> manager.command(root.literal("reload")
                        .permission(Permissions.ADMIN_RELOAD)
                        .futureHandler(context -> this.runner.run(context.sender(),
                                () -> CompletableFuture.runAsync(
                                        () -> this.reload(context.sender()), this.worker)))));
    }

    private Command.Builder<CommandActor> scoped(
            final Command.Builder<CommandActor> builder) {
        return builder.flag(this.flags.global())
                .flag(this.flags.server())
                .flag(this.flags.category())
                .flag(this.flags.scope());
    }

    private static Scope scope(final FlagContext flags) {
        if (flags.isPresent(PunishFlags.GLOBAL)) {
            return GlobalScope.INSTANCE;
        }
        final Optional<String> server = flags.getValue(PunishFlags.SERVER);
        if (server.isPresent()) {
            return ServerScope.of(server.get());
        }
        final Optional<String> category = flags.getValue(PunishFlags.CATEGORY);
        if (category.isPresent()) {
            return CategoryScope.of(category.get());
        }
        return flags.<Scope>getValue(PunishFlags.SCOPE).orElse(GlobalScope.INSTANCE);
    }

    private CompletableFuture<?> lockdown(final CommandContext<CommandActor> context) {
        final CommandActor actor = context.sender();
        final Scope scope = scope(context.flags());
        final Instant now = this.clock.instant();
        final Duration duration = context.flags().<Duration>getValue(PunishFlags.DURATION)
                .orElse(null);
        final String reason = context.flags().<String>getValue(PunishFlags.REASON)
                .map(String::strip).filter(text -> !text.isEmpty())
                .orElseGet(() -> this.config.settings().reasons().defaultReason());
        final Instant ends = duration == null ? null : now.plus(duration);
        return this.lockdowns.start(scope, reason, actor.asActor(), now, ends)
                .thenRun(() -> this.runner.send(actor, MessageKey.LOCKDOWN_STARTED,
                        Placeholder.unparsed("scope", this.scopes.format(scope)),
                        Placeholder.unparsed("ends", this.ends(actor, ends))));
    }

    private CompletableFuture<?> endLockdown(final CommandContext<CommandActor> context) {
        final CommandActor actor = context.sender();
        final Scope scope = scope(context.flags());
        return this.lockdowns.end(scope).thenAccept(ended -> this.runner.send(actor,
                ended ? MessageKey.LOCKDOWN_ENDED : MessageKey.LOCKDOWN_NOT_FOUND,
                Placeholder.unparsed("scope", this.scopes.format(scope))));
    }

    private void status(final CommandActor actor) {
        final List<Lockdown> all = this.lockdowns.lockdowns();
        if (all.isEmpty()) {
            this.runner.send(actor, MessageKey.LOCKDOWN_STATUS_NONE);
            return;
        }
        final Locale locale = this.runner.locale(actor);
        this.runner.send(actor, MessageKey.LOCKDOWN_STATUS_HEADER,
                Placeholder.unparsed("count", Integer.toString(all.size())));
        for (final Lockdown lockdown : all) {
            this.runner.send(actor, MessageKey.LOCKDOWN_STATUS_ENTRY,
                    Placeholder.unparsed("scope", this.scopes.format(lockdown.scope())),
                    Placeholder.unparsed("actor", this.placeholders.operator(
                            lockdown.startedBy().operator(), locale)),
                    Placeholder.unparsed("since", this.formats.instant(lockdown.startedAt(),
                            locale)),
                    Placeholder.unparsed("ends", this.ends(actor, lockdown.endsAt())),
                    Placeholder.unparsed("reason", lockdown.reason()));
        }
    }

    private String ends(final CommandActor actor, final @Nullable Instant ends) {
        return ends == null
                ? this.messages.renderPlain(MessageKey.TIME_NEVER, this.runner.locale(actor))
                : this.formats.instant(ends, this.runner.locale(actor));
    }

    private CompletableFuture<?> rollback(final CommandContext<CommandActor> context) {
        final CommandActor actor = context.sender();
        final String staff = context.get("staff");
        final Instant since = this.clock.instant().minus(context.<Duration>get("since"));
        final CompletableFuture<Optional<Operator>> target =
                staff.equalsIgnoreCase("console")
                        ? CompletableFuture.completedFuture(Optional.of(ConsoleOperator.INSTANCE))
                        : this.targets.resolve(new TargetInput.NameInput(staff))
                                .thenApply(resolved -> resolved instanceof ResolvedTarget.Player
                                        player
                                        ? Optional.of(PlayerOperator.of(player.uuid(),
                                                player.name() == null ? staff : player.name()))
                                        : Optional.empty());
        return target.thenCompose(found -> {
            if (found.isEmpty()) {
                this.runner.send(actor, MessageKey.COMMAND_UNKNOWN_TARGET,
                        Placeholder.unparsed("input", staff));
                return CompletableFuture.completedFuture(null);
            }
            final Operator operator = found.get();
            final var name = Placeholder.unparsed("name", staff);
            final var when = Placeholder.unparsed("since", this.formats.instant(since,
                    this.runner.locale(actor)));
            if (!context.flags().isPresent(CONFIRM)
                    || !this.confirmations.confirm(actor.name(), "staffrollback " + staff)) {
                return this.repository.activeIssuedBy(operator, since, this.clock.instant())
                        .thenAccept(active -> {
                            if (active.isEmpty()) {
                                this.runner.send(actor, MessageKey.ROLLBACK_NONE, name, when);
                                return;
                            }
                            this.confirmations.request(actor.name(), "staffrollback " + staff);
                            this.runner.send(actor, MessageKey.ROLLBACK_CONFIRM, name, when,
                                    Placeholder.unparsed("count",
                                            Integer.toString(active.size())));
                        });
            }
            return this.punishments.rollback(RollbackRequest.builder(operator, since,
                            actor.asActor())
                    .reason(context.flags().<String>getValue(PunishFlags.REASON).orElse(null))
                    .build()).thenAccept(result -> {
                        switch (result) {
                            case RollbackResult.Success success -> this.runner.send(actor,
                                    MessageKey.ROLLBACK_DONE, name, Placeholder.unparsed("count",
                                            Integer.toString(success.rolledBack().size())));
                            case RollbackResult.Failure failure -> this.runner.send(actor,
                                    failure.reason() == RollbackResult.Failure.Reason
                                            .REASON_REQUIRED
                                            ? MessageKey.ROLLBACK_FAILURE_REASON_REQUIRED
                                            : MessageKey.ROLLBACK_FAILURE_FORBIDDEN);
                        }
                    });
        });
    }

    private CompletableFuture<?> prune(final CommandContext<CommandActor> context) {
        final CommandActor actor = context.sender();
        final TargetInput input = context.get("target");
        final Instant before = context.flags().<Duration>getValue(OLDER_THAN)
                .map(duration -> this.clock.instant().minus(duration)).orElse(null);
        return this.targets.resolve(input).thenCompose(target -> {
            final Victim victim = switch (target) {
                case ResolvedTarget.Player player ->
                        PlayerVictim.of(player.uuid());
                case ResolvedTarget.Address address ->
                        AddressVictim.of(address.address());
                case ResolvedTarget.Range range -> range.range();
                default -> null;
            };
            if (victim == null) {
                this.runner.send(actor, MessageKey.COMMAND_ID_NOT_ALLOWED);
                return CompletableFuture.completedFuture(null);
            }
            final var name = Placeholder.unparsed("name", this.replies.victim(actor, victim));
            final String command = "prunehistory " + this.replies.victim(actor, victim);
            return this.repository.endedOf(victim, before).thenCompose(ended -> {
                if (ended.isEmpty()) {
                    this.runner.send(actor, MessageKey.PRUNE_NONE, name);
                    return CompletableFuture.completedFuture(null);
                }
                if (!context.flags().isPresent(CONFIRM)
                        || !this.confirmations.confirm(actor.name(), command)) {
                    this.confirmations.request(actor.name(), command);
                    this.runner.send(actor, MessageKey.PRUNE_CONFIRM, name,
                            Placeholder.unparsed("count", Integer.toString(ended.size())));
                    return CompletableFuture.completedFuture(null);
                }
                final List<CompletableFuture<ExpungeResult>> expunges = new ArrayList<>();
                for (final Punishment punishment : ended) {
                    expunges.add(this.punishments.expunge(punishment.id(), actor.asActor()));
                }
                return CompletableFuture.allOf(expunges.toArray(CompletableFuture[]::new))
                        .thenRun(() -> this.runner.send(actor, MessageKey.PRUNE_DONE, name,
                                Placeholder.unparsed("count", Long.toString(expunges.stream()
                                        .filter(done -> done.join()
                                                instanceof ExpungeResult.Success)
                                        .count())),
                                Placeholder.unparsed("total", Integer.toString(ended.size()))));
            });
        });
    }

    private CompletableFuture<?> serverStatus(final CommandActor actor) {
        return this.punishments.count(PunishmentQuery.builder().activeOnly(true).build())
                .thenAccept(active -> this.runner.send(actor, MessageKey.STATUS_BODY,
                        Placeholder.unparsed("version", BuildConstants.VERSION),
                        Placeholder.unparsed("commit", BuildConstants.GIT_COMMIT),
                        Placeholder.unparsed("platform", this.platform.platformVersion()),
                        Placeholder.unparsed("server", this.scopes.originServer().orElse("?")),
                        Placeholder.unparsed("storage", this.config.storage().type().name()),
                        Placeholder.unparsed("sync", this.config.storage().sync().mode().name()),
                        Placeholder.unparsed("active", Long.toString(active)),
                        Placeholder.unparsed("lockdowns",
                                Integer.toString(this.lockdowns.lockdowns().size()))));
    }

    private void reload(final CommandActor actor) {
        final ReloadResult result = this.config.reload();
        switch (result) {
            case ReloadResult.Reloaded reloaded -> {
                this.messages.load();
                this.scopes.checkServerName();
                this.runner.send(actor, MessageKey.RELOAD_DONE);
                if (!reloaded.restartRequired().isEmpty()) {
                    this.runner.send(actor, MessageKey.RELOAD_RESTART, Placeholder.unparsed(
                            "files", String.join(", ", reloaded.restartRequired())));
                }
            }
            case ReloadResult.Failed failed -> {
                this.runner.send(actor, MessageKey.RELOAD_FAILED);
                for (final ConfigIssue issue : failed.errors()) {
                    this.runner.send(actor, MessageKey.RELOAD_ERROR,
                            Placeholder.unparsed("error", issue.toString()));
                }
            }
        }
    }
}
