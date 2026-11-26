package net.serlith.sakurabans.core.command.module;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.UnaryOperator;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.serlith.sakurabans.api.model.AddressStrictness;
import net.serlith.sakurabans.api.model.ConsoleOperator;
import net.serlith.sakurabans.api.model.Operator;
import net.serlith.sakurabans.api.model.PlayerOperator;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.punish.PunishmentQuery;
import net.serlith.sakurabans.core.command.CommandActor;
import net.serlith.sakurabans.core.command.CommandModule;
import net.serlith.sakurabans.core.command.CommandRegistrar;
import net.serlith.sakurabans.core.command.CommandRunner;
import net.serlith.sakurabans.core.command.Pager;
import net.serlith.sakurabans.core.command.parser.TargetParser;
import net.serlith.sakurabans.core.message.MessageKey;
import net.serlith.sakurabans.core.model.ResolvedTarget;
import net.serlith.sakurabans.core.model.TargetInput;
import net.serlith.sakurabans.core.permission.Permissions;
import net.serlith.sakurabans.core.player.TargetResolver;
import net.serlith.sakurabans.core.punish.SakuraPunishmentService;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.parser.standard.IntegerParser;
import org.incendo.cloud.permission.Permission;

public final class HistoryCommands implements CommandModule {

    static final int PAGE_SIZE = 10;
    private static final String CONSOLE = "console";

    private record ListSpec(String name, PunishmentType type, String permission,
            MessageKey description, MessageKey header) {
    }

    private static final List<ListSpec> LISTS = List.of(
            new ListSpec("banlist", PunishmentType.BAN, Permissions.LIST_BANLIST,
                    MessageKey.COMMAND_DESCRIPTION_BANLIST, MessageKey.LIST_HEADER_BANLIST),
            new ListSpec("mutelist", PunishmentType.MUTE, Permissions.LIST_MUTELIST,
                    MessageKey.COMMAND_DESCRIPTION_MUTELIST, MessageKey.LIST_HEADER_MUTELIST),
            new ListSpec("warnlist", PunishmentType.WARN, Permissions.LIST_WARNLIST,
                    MessageKey.COMMAND_DESCRIPTION_WARNLIST, MessageKey.LIST_HEADER_WARNLIST));

    private final CommandRegistrar registrar;
    private final CommandRunner runner;
    private final Pager pager;
    private final TargetParser targetParser;
    private final TargetResolver targets;
    private final SakuraPunishmentService punishments;

    public HistoryCommands(final CommandRegistrar registrar, final CommandRunner runner,
            final Pager pager, final TargetParser targetParser, final TargetResolver targets,
            final SakuraPunishmentService punishments) {
        this.registrar = registrar;
        this.runner = runner;
        this.pager = pager;
        this.targetParser = targetParser;
        this.targets = targets;
        this.punishments = punishments;
    }

    @Override
    public void register(final CommandManager<CommandActor> manager) {
        this.registrar.builder(manager, "history", MessageKey.COMMAND_DESCRIPTION_HISTORY)
                .ifPresent(builder -> manager.command(builder
                        .permission(Permissions.LIST_HISTORY)
                        .required("target", this.targetParser.descriptor())
                        .optional("page", IntegerParser.integerParser(1))
                        .futureHandler(context -> this.runner.run(context.sender(),
                                () -> this.history(context)))));
        this.registrar.builder(manager, "staffhistory",
                        MessageKey.COMMAND_DESCRIPTION_STAFFHISTORY)
                .ifPresent(builder -> manager.command(builder
                        .permission(Permissions.LIST_STAFFHISTORY)
                        .required("staff", this.targetParser.descriptor())
                        .optional("page", IntegerParser.integerParser(1))
                        .futureHandler(context -> this.runner.run(context.sender(),
                                () -> this.staffHistory(context)))));
        this.registrar.builder(manager, "warns", MessageKey.COMMAND_DESCRIPTION_WARNS)
                .ifPresent(builder -> manager.command(builder
                        .permission(Permission.anyOf(Permission.of(Permissions.LIST_WARNS),
                                Permission.of(Permissions.LIST_WARNS_SELF)))
                        .optional("target", this.targetParser.descriptor())
                        .optional("page", IntegerParser.integerParser(1))
                        .futureHandler(context -> this.runner.run(context.sender(),
                                () -> this.warns(context)))));
        for (final ListSpec spec : LISTS) {
            this.registrar.builder(manager, spec.name(), spec.description())
                    .ifPresent(builder -> manager.command(builder
                            .permission(spec.permission())
                            .optional("page", IntegerParser.integerParser(1))
                            .futureHandler(context -> this.runner.run(context.sender(),
                                    () -> this.list(context, spec)))));
        }
    }

    private CompletableFuture<?> history(final CommandContext<CommandActor> context) {
        final CommandActor actor = context.sender();
        final int page = context.<Integer>optional("page").orElse(1);
        return this.targets.resolve(context.get("target")).thenCompose(target -> {
            final Optional<UnaryOperator<PunishmentQuery.Builder>> filter =
                    this.filter(actor, target);
            if (filter.isEmpty()) {
                return CompletableFuture.completedFuture(null);
            }
            final String name = targetName(target);
            return this.punishments.query(filter.get().apply(PunishmentQuery.builder())
                            .page(page).pageSize(PAGE_SIZE).build())
                    .thenAccept(found -> this.pager.punishments(actor,
                            MessageKey.HISTORY_HEADER, Placeholder.unparsed("name", name), found,
                            this.label("history") + " " + name));
        });
    }

    private CompletableFuture<?> staffHistory(final CommandContext<CommandActor> context) {
        final CommandActor actor = context.sender();
        final int page = context.<Integer>optional("page").orElse(1);
        final TargetInput input = context.get("staff");
        final CompletableFuture<Optional<Operator>> operator =
                input instanceof TargetInput.NameInput(String name1)
                        && name1.toLowerCase(Locale.ROOT).equals(CONSOLE)
                ? CompletableFuture.completedFuture(Optional.of(ConsoleOperator.INSTANCE))
                : this.targets.resolve(input).thenApply(target -> {
                    if (target instanceof ResolvedTarget.Player player) {
                        return Optional.of(PlayerOperator.of(player.uuid(),
                                PunishCommands.displayName(player)));
                    }
                    this.runner.send(actor, MessageKey.COMMAND_UNKNOWN_TARGET,
                            Placeholder.unparsed("input", PunishCommands.inputText(input)));
                    return Optional.empty();
                });
        return operator.thenCompose(found -> {
            if (found.isEmpty()) {
                return CompletableFuture.completedFuture(null);
            }
            final String name = found.get() instanceof PlayerOperator player
                    ? player.name() : CONSOLE;
            return this.punishments.query(PunishmentQuery.builder().operator(found.get())
                            .page(page).pageSize(PAGE_SIZE).build())
                    .thenAccept(result -> this.pager.punishments(actor,
                            MessageKey.STAFFHISTORY_HEADER, Placeholder.unparsed("name", name),
                            result, this.label("staffhistory") + " " + name));
        });
    }

    private CompletableFuture<?> warns(final CommandContext<CommandActor> context) {
        final CommandActor actor = context.sender();
        final int page = context.<Integer>optional("page").orElse(1);
        final Optional<TargetInput> input = context.optional("target");
        final CompletableFuture<ResolvedTarget> target;
        if (input.isPresent()) {
            if (!actor.hasPermission(Permissions.LIST_WARNS)) {
                this.runner.send(actor, MessageKey.ERROR_NO_PERMISSION);
                return CompletableFuture.completedFuture(null);
            }
            target = this.targets.resolve(input.get());
        } else {
            final Optional<UUID> self = actor.uuid();
            if (self.isEmpty()) {
                this.runner.send(actor, MessageKey.COMMAND_PLAYERS_ONLY);
                return CompletableFuture.completedFuture(null);
            }
            if (!actor.hasPermission(Permissions.LIST_WARNS_SELF)) {
                this.runner.send(actor, MessageKey.ERROR_NO_PERMISSION);
                return CompletableFuture.completedFuture(null);
            }
            target = CompletableFuture.completedFuture(
                    new ResolvedTarget.Player(self.get(), actor.name(), null));
        }
        return target.thenCompose(found -> {
            final Optional<UnaryOperator<PunishmentQuery.Builder>> filter =
                    this.filter(actor, found);
            if (filter.isEmpty()) {
                return CompletableFuture.completedFuture(null);
            }
            final String name = targetName(found);
            return this.punishments.query(filter.get().apply(PunishmentQuery.builder())
                            .types(PunishmentType.WARN).activeOnly(true)
                            .page(page).pageSize(PAGE_SIZE).build())
                    .thenAccept(result -> this.pager.punishments(actor,
                            MessageKey.WARNS_HEADER, Placeholder.unparsed("name", name), result,
                            this.label("warns") + " " + name));
        });
    }

    private CompletableFuture<?> list(final CommandContext<CommandActor> context,
            final ListSpec spec) {
        final CommandActor actor = context.sender();
        final int page = context.<Integer>optional("page").orElse(1);
        return this.punishments.query(PunishmentQuery.builder().types(spec.type())
                        .activeOnly(true).page(page).pageSize(PAGE_SIZE).build())
                .thenAccept(result -> this.pager.punishments(actor, spec.header(),
                        TagResolver.empty(),
                        result, this.label(spec.name())));
    }

    /** What a target's history is: what names it, or (a range) that exact range. */
    private Optional<UnaryOperator<PunishmentQuery.Builder>> filter(final CommandActor actor,
            final ResolvedTarget target) {
        return switch (target) {
            case ResolvedTarget.Player player -> Optional.of(query -> query.applicableTo(
                    PunishmentQuery.Applicability.of(player.uuid(), null,
                            AddressStrictness.LENIENT)));
            case ResolvedTarget.Address address -> Optional.of(query -> query.applicableTo(
                    PunishmentQuery.Applicability.of(null, address.address(),
                            AddressStrictness.LENIENT)));
            case ResolvedTarget.Unknown unknown -> {
                this.runner.send(actor, MessageKey.COMMAND_UNKNOWN_TARGET,
                        Placeholder.unparsed("input", PunishCommands.inputText(unknown.input())));
                yield Optional.empty();
            }
            case ResolvedTarget.Range range -> Optional.of(query -> query.victim(range.range()));
            case ResolvedTarget.PunishmentId id -> {
                this.runner.send(actor, MessageKey.COMMAND_ID_NOT_ALLOWED);
                yield Optional.empty();
            }
        };
    }

    private String label(final String command) {
        return this.registrar.label(command).orElse(command);
    }

    static String targetName(final ResolvedTarget target) {
        return switch (target) {
            case ResolvedTarget.Player player -> PunishCommands.displayName(player);
            case ResolvedTarget.Address address -> address.address().getHostAddress();
            case ResolvedTarget.Range range -> range.range().network().getHostAddress() + "/"
                    + range.range().prefixLength();
            case ResolvedTarget.PunishmentId id -> "#" + id.id();
            case ResolvedTarget.Unknown unknown -> PunishCommands.inputText(unknown.input());
        };
    }
}
