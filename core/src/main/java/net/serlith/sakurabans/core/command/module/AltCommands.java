package net.serlith.sakurabans.core.command.module;

import java.net.InetAddress;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.serlith.sakurabans.api.model.AddressRecord;
import net.serlith.sakurabans.api.model.PlayerProfileView;
import net.serlith.sakurabans.core.command.CommandActor;
import net.serlith.sakurabans.core.command.CommandModule;
import net.serlith.sakurabans.core.command.CommandRegistrar;
import net.serlith.sakurabans.core.command.CommandRunner;
import net.serlith.sakurabans.core.command.Replies;
import net.serlith.sakurabans.core.command.parser.TargetParser;
import net.serlith.sakurabans.core.message.Formats;
import net.serlith.sakurabans.core.message.MessageKey;
import net.serlith.sakurabans.core.model.ResolvedTarget;
import net.serlith.sakurabans.core.permission.Permissions;
import net.serlith.sakurabans.core.player.TargetResolver;
import net.serlith.sakurabans.core.storage.repository.PlayerRepository;
import net.serlith.sakurabans.core.util.Futures;
import net.serlith.sakurabans.core.util.InetAddresses;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.parser.standard.StringParser;

public final class AltCommands implements CommandModule {

    private final CommandRegistrar registrar;
    private final CommandRunner runner;
    private final Replies replies;
    private final Formats formats;
    private final TargetParser targetParser;
    private final TargetResolver targets;
    private final PlayerRepository players;

    public AltCommands(final CommandRegistrar registrar, final CommandRunner runner,
            final Replies replies, final Formats formats, final TargetParser targetParser,
            final TargetResolver targets, final PlayerRepository players) {
        this.registrar = registrar;
        this.runner = runner;
        this.replies = replies;
        this.formats = formats;
        this.targetParser = targetParser;
        this.targets = targets;
        this.players = players;
    }

    @Override
    public void register(final CommandManager<CommandActor> manager) {
        this.registrar.builder(manager, "alts", MessageKey.COMMAND_DESCRIPTION_ALTS)
                .ifPresent(builder -> manager.command(builder.permission(Permissions.ALTS_CHECK)
                        .required("target", this.targetParser.descriptor())
                        .futureHandler(context -> this.runner.run(context.sender(),
                                () -> this.player(context, this::alts)))));
        this.registrar.builder(manager, "iphistory", MessageKey.COMMAND_DESCRIPTION_IPHISTORY)
                .ifPresent(builder -> manager.command(builder
                        .permission(Permissions.ALTS_IPHISTORY)
                        .required("target", this.targetParser.descriptor())
                        .futureHandler(context -> this.runner.run(context.sender(),
                                () -> this.player(context, this::ipHistory)))));
        this.registrar.builder(manager, "namehistory",
                        MessageKey.COMMAND_DESCRIPTION_NAMEHISTORY)
                .ifPresent(builder -> manager.command(builder
                        .permission(Permissions.ALTS_NAMEHISTORY)
                        .required("target", this.targetParser.descriptor())
                        .futureHandler(context -> this.runner.run(context.sender(),
                                () -> this.player(context, this::nameHistory)))));
        this.registrar.builder(manager, "lastuuid", MessageKey.COMMAND_DESCRIPTION_LASTUUID)
                .ifPresent(builder -> manager.command(builder
                        .permission(Permissions.ALTS_LASTUUID)
                        .required("name", StringParser.stringParser())
                        .futureHandler(context -> this.runner.run(context.sender(),
                                () -> this.lastUuid(context)))));
    }

    @FunctionalInterface
    private interface PlayerAction {
        CompletableFuture<?> run(CommandActor actor, ResolvedTarget.Player player);
    }

    private CompletableFuture<?> player(final CommandContext<CommandActor> context,
            final PlayerAction action) {
        final CommandActor actor = context.sender();
        return this.targets.resolve(context.get("target")).thenCompose(target -> {
            if (target instanceof ResolvedTarget.Player player) {
                return action.run(actor, player);
            }
            this.runner.send(actor, MessageKey.COMMAND_PLAYER_REQUIRED,
                    Placeholder.unparsed("input", HistoryCommands.targetName(target)));
            return CompletableFuture.completedFuture(null);
        });
    }

    private CompletableFuture<?> alts(final CommandActor actor,
            final ResolvedTarget.Player player) {
        final String name = PunishCommands.displayName(player);
        return this.players.addresses(player.uuid()).thenCompose(addresses -> {
            final List<CompletableFuture<List<PlayerProfileView>>> lookups = addresses.stream()
                    .map(AddressRecord::address)
                    .distinct()
                    .map(this.players::accountsAt)
                    .toList();
            return Futures.allOf(lookups).thenAccept(results -> {
                final Map<UUID, PlayerProfileView> alts = new LinkedHashMap<>();
                results.forEach(accounts -> accounts.forEach(account -> {
                    if (!account.uuid().equals(player.uuid())) {
                        alts.putIfAbsent(account.uuid(), account);
                    }
                }));
                if (alts.isEmpty()) {
                    this.runner.send(actor, MessageKey.ALTS_NONE,
                            Placeholder.unparsed("name", name));
                    return;
                }
                this.runner.send(actor, MessageKey.ALTS_HEADER, Placeholder.unparsed("name", name),
                        Placeholder.unparsed("count", Integer.toString(alts.size())));
                alts.values().forEach(alt -> this.runner.send(actor, MessageKey.ALTS_ENTRY,
                        Placeholder.unparsed("name", alt.name()),
                        Placeholder.unparsed("last_seen", this.formats.instant(alt.lastSeen(),
                                this.runner.locale(actor)))));
            });
        });
    }

    private CompletableFuture<?> ipHistory(final CommandActor actor,
            final ResolvedTarget.Player player) {
        final String name = PunishCommands.displayName(player);
        return this.players.addresses(player.uuid()).thenAccept(addresses -> {
            if (addresses.isEmpty()) {
                this.runner.send(actor, MessageKey.IPHISTORY_NONE,
                        Placeholder.unparsed("name", name));
                return;
            }
            this.runner.send(actor, MessageKey.IPHISTORY_HEADER,
                        Placeholder.unparsed("name", name));
            for (final AddressRecord record : addresses) {
                this.runner.send(actor, MessageKey.IPHISTORY_ENTRY,
                        Placeholder.unparsed("address", this.address(actor, record.address())),
                        Placeholder.unparsed("first_seen", this.formats.instant(
                                record.firstSeen(), this.runner.locale(actor))),
                        Placeholder.unparsed("last_seen", this.formats.instant(
                                record.lastSeen(), this.runner.locale(actor))),
                        Placeholder.unparsed("server", record.lastServer().orElse("")));
            }
        });
    }

    private CompletableFuture<?> nameHistory(final CommandActor actor,
            final ResolvedTarget.Player player) {
        final String name = PunishCommands.displayName(player);
        return this.players.names(player.uuid()).thenAccept(names -> {
            if (names.isEmpty()) {
                this.runner.send(actor, MessageKey.NAMEHISTORY_NONE,
                        Placeholder.unparsed("name", name));
                return;
            }
            this.runner.send(actor, MessageKey.NAMEHISTORY_HEADER,
                        Placeholder.unparsed("name", name));
            names.forEach(record -> this.runner.send(actor, MessageKey.NAMEHISTORY_ENTRY,
                    Placeholder.unparsed("name", record.name()),
                    Placeholder.unparsed("first_seen", this.formats.instant(record.firstSeen(),
                            this.runner.locale(actor))),
                    Placeholder.unparsed("last_seen", this.formats.instant(record.lastSeen(),
                            this.runner.locale(actor)))));
        });
    }

    private CompletableFuture<?> lastUuid(final CommandContext<CommandActor> context) {
        final CommandActor actor = context.sender();
        final String name = context.get("name");
        return this.players.lastUuid(name).thenAccept((Optional<UUID> uuid) -> uuid.ifPresentOrElse(
                found -> this.runner.send(actor, MessageKey.LASTUUID_RESULT,
                        Placeholder.unparsed("name", name),
                        Placeholder.unparsed("uuid", found.toString())),
                () -> this.runner.send(actor, MessageKey.LASTUUID_NONE,
                        Placeholder.unparsed("name", name))));
    }

    private String address(final CommandActor actor, final InetAddress address) {
        return this.replies.showAddresses(actor) ? address.getHostAddress()
                : InetAddresses.censor(address);
    }
}
