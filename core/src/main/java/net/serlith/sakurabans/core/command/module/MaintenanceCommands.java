package net.serlith.sakurabans.core.command.module;

import java.time.InstantSource;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.model.Scope;
import net.serlith.sakurabans.api.model.ServerScope;
import net.serlith.sakurabans.core.command.CommandActor;
import net.serlith.sakurabans.core.command.CommandModule;
import net.serlith.sakurabans.core.command.CommandRegistrar;
import net.serlith.sakurabans.core.command.CommandRunner;
import net.serlith.sakurabans.core.command.ConfirmationTracker;
import net.serlith.sakurabans.core.command.PunishFlags;
import net.serlith.sakurabans.core.command.SakuraCommands;
import net.serlith.sakurabans.core.command.parser.ScopeFilter;
import net.serlith.sakurabans.core.message.MessageKey;
import net.serlith.sakurabans.core.permission.Permissions;
import net.serlith.sakurabans.core.player.NameCache;
import net.serlith.sakurabans.core.punish.ResetService;
import net.serlith.sakurabans.core.scope.SakuraScopeService;
import net.serlith.sakurabans.core.storage.repository.PlayerRepository;
import net.serlith.sakurabans.core.storage.repository.PunishmentResetRepository;
import net.serlith.sakurabans.core.util.InetAddresses;
import net.serlith.sakurabans.core.util.Strings;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.parser.flag.CommandFlag;
import org.incendo.cloud.parser.flag.FlagContext;
import org.incendo.cloud.parser.standard.EnumParser;
import org.incendo.cloud.parser.standard.StringParser;
import org.incendo.cloud.parser.standard.UUIDParser;
import org.incendo.cloud.permission.PredicatePermission;

public final class MaintenanceCommands implements CommandModule {

    private static final String CONFIRM = "confirm";
    private static final String MODE = "mode";
    private static final String EXCLUDE = "exclude";

    private final CommandRegistrar registrar;
    private final CommandRunner runner;
    private final PunishFlags flags;
    private final ResetService resets;
    private final PlayerRepository players;
    private final NameCache names;
    private final SakuraScopeService scopes;
    private final ConfirmationTracker confirmations;
    private final InstantSource clock;

    public MaintenanceCommands(final CommandRegistrar registrar, final CommandRunner runner,
            final PunishFlags flags, final ResetService resets, final PlayerRepository players,
            final NameCache names, final SakuraScopeService scopes,
            final ConfirmationTracker confirmations, final InstantSource clock) {
        this.registrar = registrar;
        this.runner = runner;
        this.flags = flags;
        this.resets = resets;
        this.players = players;
        this.names = names;
        this.scopes = scopes;
        this.confirmations = confirmations;
        this.clock = clock;
    }

    @Override
    public void register(final CommandManager<CommandActor> manager) {
        this.registrar.builder(manager, SakuraCommands.ROOT, MessageKey.COMMAND_DESCRIPTION_RESET)
                .ifPresent(root -> manager.command(root.literal("reset")
                        .permission(PredicatePermission.<CommandActor>of(CommandActor::console))
                        .flag(CommandFlag.<CommandActor>builder(MODE)
                                .withComponent(EnumParser.enumParser(
                                        PunishmentResetRepository.Mode.class)).build())
                        .flag(this.flags.scopeFilter())
                        .flag(CommandFlag.<CommandActor>builder(EXCLUDE)
                                .withComponent(EnumParser.enumParser(PunishmentType.class))
                                .asRepeatable().build())
                        .flag(CommandFlag.builder(CONFIRM).build())
                        .futureHandler(context -> this.runner.run(context.sender(),
                                () -> this.reset(context)))));
        this.registrar.builder(manager, SakuraCommands.ROOT,
                        MessageKey.COMMAND_DESCRIPTION_ADDLOGIN)
                .ifPresent(root -> manager.command(root.literal("addlogin")
                        .permission(Permissions.ADMIN_ADDLOGIN)
                        .required("name", StringParser.stringParser())
                        .required("uuid", UUIDParser.uuidParser())
                        .required("address", StringParser.stringParser())
                        .futureHandler(context -> this.runner.run(context.sender(),
                                () -> this.addLogin(context)))));
    }

    private CompletableFuture<?> reset(final CommandContext<CommandActor> context) {
        final CommandActor actor = context.sender();
        final FlagContext given = context.flags();
        if (!actor.console()) {
            this.runner.send(actor, MessageKey.RESET_CONSOLE_ONLY);
            return CompletableFuture.completedFuture(null);
        }
        final Optional<PunishmentResetRepository.Mode> mode = given.getValue(MODE);
        if (mode.isEmpty()) {
            this.runner.send(actor, MessageKey.RESET_NO_MODE);
            return CompletableFuture.completedFuture(null);
        }
        final Set<PunishmentType> types = EnumSet.allOf(PunishmentType.class);
        given.<PunishmentType>getAll(EXCLUDE).forEach(types::remove);
        // No --scope and --scope all both mean every scope.
        final Scope scope = given.<ScopeFilter>getValue(PunishFlags.SCOPE)
                .flatMap(ScopeFilter::scope).orElse(null);
        final PunishmentResetRepository.Filter filter =
                new PunishmentResetRepository.Filter(types, scope);
        final String command = "reset " + mode.get() + " " + (scope == null ? "all"
                : this.scopes.format(scope)) + " " + types.stream().map(Enum::name).sorted()
                .collect(Collectors.joining(","));
        return this.resets.preview(filter).thenCompose(counts -> {
            if (counts.affected(mode.get()) == 0) {
                this.runner.send(actor, MessageKey.RESET_NONE);
                return CompletableFuture.completedFuture(null);
            }
            if (!given.isPresent(CONFIRM) || !this.confirmations.confirm(actor.name(), command)) {
                this.confirmations.request(actor.name(), command);
                this.runner.send(actor, MessageKey.RESET_CONFIRM,
                        Placeholder.component("action", this.runner.render(actor,
                                actionKey(mode.get()))),
                        Placeholder.unparsed("count",
                                Long.toString(counts.affected(mode.get()))),
                        Placeholder.unparsed("active", Long.toString(counts.active())),
                        Placeholder.unparsed("ended", Long.toString(counts.ended())),
                        Placeholder.unparsed("scope", scope == null ? "all scopes"
                                : this.scopes.format(scope)),
                        Placeholder.unparsed("types", types.stream()
                                .map(type -> type.name().toLowerCase(Locale.ROOT))
                                .collect(Collectors.joining(", "))));
                return CompletableFuture.completedFuture(null);
            }
            return this.resets.reset(mode.get(), filter, actor.asActor())
                    .thenAccept(count -> this.runner.send(actor, doneKey(mode.get()),
                            Placeholder.unparsed("count", Long.toString(count))));
        });
    }

    private static MessageKey actionKey(final PunishmentResetRepository.Mode mode) {
        return switch (mode) {
            case DELETE -> MessageKey.RESET_ACTION_DELETE;
            case DEACTIVATE -> MessageKey.RESET_ACTION_DEACTIVATE;
            case PRUNE -> MessageKey.RESET_ACTION_PRUNE;
        };
    }

    private static MessageKey doneKey(final PunishmentResetRepository.Mode mode) {
        return switch (mode) {
            case DELETE -> MessageKey.RESET_DONE_DELETE;
            case DEACTIVATE -> MessageKey.RESET_DONE_DEACTIVATE;
            case PRUNE -> MessageKey.RESET_DONE_PRUNE;
        };
    }

    private CompletableFuture<?> addLogin(final CommandContext<CommandActor> context) {
        final CommandActor actor = context.sender();
        final String name = context.get("name");
        final UUID uuid = context.get("uuid");
        final String text = context.get("address");
        if (!Strings.isValidPlayerName(name)) {
            this.runner.send(actor, MessageKey.ADDLOGIN_INVALID_NAME,
                    Placeholder.unparsed("input", name));
            return CompletableFuture.completedFuture(null);
        }
        final var address = InetAddresses.parseLiteral(text);
        if (address.isEmpty()) {
            this.runner.send(actor, MessageKey.ADDLOGIN_INVALID_ADDRESS,
                    Placeholder.unparsed("input", text));
            return CompletableFuture.completedFuture(null);
        }
        final TagResolver values = TagResolver.resolver(Placeholder.unparsed("name", name),
                Placeholder.unparsed("uuid", uuid.toString()));
        // As LoginGate records a join: the profile, the name and the address, on this server.
        return this.players.recordConnection(uuid, name, this.clock.instant(),
                        InetAddresses.normalize(address.get()),
                        this.scopes.thisServer().map(ServerScope::server).orElse(null))
                .thenRun(() -> {
                    this.names.put(uuid, name);
                    this.runner.send(actor, MessageKey.ADDLOGIN_DONE, values);
                });
    }
}
