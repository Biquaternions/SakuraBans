package net.serlith.sakurabans.core.command.module;

import java.net.InetAddress;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.serlith.sakurabans.api.model.AddressRecord;
import net.serlith.sakurabans.api.model.AddressStrictness;
import net.serlith.sakurabans.api.model.AddressVictim;
import net.serlith.sakurabans.api.player.AltEntry;
import net.serlith.sakurabans.core.alts.AltService;
import net.serlith.sakurabans.core.command.CommandActor;
import net.serlith.sakurabans.core.command.CommandModule;
import net.serlith.sakurabans.core.command.CommandRegistrar;
import net.serlith.sakurabans.core.command.CommandRunner;
import net.serlith.sakurabans.core.command.Replies;
import net.serlith.sakurabans.core.command.SakuraCommands;
import net.serlith.sakurabans.core.command.parser.TargetParser;
import net.serlith.sakurabans.core.configuration.ConfigService;
import net.serlith.sakurabans.core.message.Formats;
import net.serlith.sakurabans.core.message.MessageKey;
import net.serlith.sakurabans.core.message.Placeholders;
import net.serlith.sakurabans.core.model.ResolvedTarget;
import net.serlith.sakurabans.core.permission.Permissions;
import net.serlith.sakurabans.core.platform.OnlinePlayer;
import net.serlith.sakurabans.core.platform.Platform;
import net.serlith.sakurabans.core.player.TargetResolver;
import net.serlith.sakurabans.core.storage.repository.PlayerRepository;
import net.serlith.sakurabans.core.util.InetAddresses;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.parser.standard.EnumParser;
import org.incendo.cloud.parser.standard.StringParser;

public final class AltCommands implements CommandModule {

    private final CommandRegistrar registrar;
    private final CommandRunner runner;
    private final Replies replies;
    private final Formats formats;
    private final TargetParser targetParser;
    private final TargetResolver targets;
    private final PlayerRepository players;
    private final InstantSource clock;
    private final AltService alts;
    private final Platform platform;
    private final ConfigService config;

    public AltCommands(final CommandRegistrar registrar, final CommandRunner runner,
            final Replies replies, final Formats formats, final TargetParser targetParser,
            final TargetResolver targets, final PlayerRepository players,
            final InstantSource clock, final AltService alts, final Platform platform,
            final ConfigService config) {
        this.registrar = registrar;
        this.runner = runner;
        this.replies = replies;
        this.formats = formats;
        this.targetParser = targetParser;
        this.targets = targets;
        this.players = players;
        this.clock = clock;
        this.alts = alts;
        this.platform = platform;
        this.config = config;
    }

    @Override
    public void register(final CommandManager<CommandActor> manager) {
        this.registrar.builder(manager, "alts", MessageKey.COMMAND_DESCRIPTION_ALTS)
                .ifPresent(builder -> manager.command(builder.permission(Permissions.ALTS_CHECK)
                        .required("target", this.targetParser.descriptor())
                        .optional("strictness", EnumParser.enumParser(AddressStrictness.class))
                        .futureHandler(context -> this.runner.run(context.sender(),
                                () -> this.player(context, (actor, player) -> this.alts(actor,
                                        player, context.<AddressStrictness>optional("strictness")
                                                .orElseGet(() -> this.config.settings()
                                                        .enforcement().addressStrictness())))))));
        this.registrar.builder(manager, "ipreport", MessageKey.COMMAND_DESCRIPTION_IPREPORT)
                .ifPresent(builder -> manager.command(builder
                        .permission(Permissions.ALTS_IPREPORT)
                        .handler(context -> this.ipReport(context.sender()))));
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
        this.registrar.builder(manager, SakuraCommands.ROOT,
                        MessageKey.COMMAND_DESCRIPTION_UNLINK)
                .ifPresent(builder -> manager.command(builder.literal("unlink")
                        .permission(Permissions.ALTS_UNLINK)
                        .required("target", this.targetParser.descriptor())
                        .optional("address", StringParser.stringParser())
                        .futureHandler(context -> this.runner.run(context.sender(),
                                () -> this.player(context, (actor, player) ->
                                        this.unlink(context, actor, player))))));
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
            final ResolvedTarget.Player player, final AddressStrictness strictness) {
        final String name = PunishCommands.displayName(player);
        return this.alts.alts(player.uuid(), strictness).thenAccept(alts -> {
            if (alts.isEmpty()) {
                this.runner.send(actor, MessageKey.ALTS_NONE, Placeholder.unparsed("name", name));
                return;
            }
            final Locale locale = this.runner.locale(actor);
            this.runner.send(actor, MessageKey.ALTS_HEADER, Placeholder.unparsed("name", name),
                    Placeholder.unparsed("count", Integer.toString(alts.size())),
                    Placeholder.unparsed("strictness",
                            strictness.name().toLowerCase(Locale.ROOT)));
            for (final AltEntry alt : alts) {
                this.runner.send(actor, MessageKey.ALTS_ENTRY,
                        Placeholder.unparsed("name", alt.account().name()),
                        Placeholder.unparsed("last_seen",
                                this.formats.instant(alt.account().lastSeen(), locale)),
                        Placeholder.unparsed("addresses", alt.sharedAddresses().stream()
                                .map(address -> this.replies.victim(actor,
                                        AddressVictim.of(address)))
                                .collect(Collectors.joining(", "))),
                        Placeholder.component("punished", alt.activePunishments().isEmpty()
                                ? Component.empty()
                                : this.runner.render(actor, MessageKey.ALTS_PUNISHED,
                                        Placeholder.component("types", Component.join(
                                                JoinConfiguration.commas(true),
                                                alt.activePunishments().stream().sorted()
                                                        .map(type -> this.runner.render(actor,
                                                                Placeholders.typeKey(type)))
                                                        .toList())))));
            }
        });
    }

    /** Online players grouped by the address they joined from, for shared addresses only. */
    private void ipReport(final CommandActor actor) {
        final Map<InetAddress, List<String>> groups = new LinkedHashMap<>();
        for (final OnlinePlayer player : this.platform.onlinePlayers()) {
            groups.computeIfAbsent(player.address(), ignored -> new ArrayList<>())
                    .add(player.name());
        }
        groups.values().removeIf(names -> names.size() < 2);
        if (groups.isEmpty()) {
            this.runner.send(actor, MessageKey.IPREPORT_NONE);
            return;
        }
        this.runner.send(actor, MessageKey.IPREPORT_HEADER,
                Placeholder.unparsed("count", Integer.toString(groups.size())));
        groups.forEach((address, names) -> this.runner.send(actor, MessageKey.IPREPORT_ENTRY,
                Placeholder.unparsed("address", this.replies.victim(actor,
                        AddressVictim.of(address))),
                Placeholder.unparsed("names", String.join(", ", names.stream().sorted()
                        .toList()))));
    }

    private CompletableFuture<?> unlink(final CommandContext<CommandActor> context,
            final CommandActor actor, final ResolvedTarget.Player player) {
        final String name = PunishCommands.displayName(player);
        final Optional<String> text = context.optional("address");
        final InetAddress address = text.flatMap(InetAddresses::parseLiteral).orElse(null);
        if (text.isPresent() && address == null) {
            this.runner.send(actor, MessageKey.UNLINK_NOT_ADDRESS,
                    Placeholder.unparsed("input", text.get()));
            return CompletableFuture.completedFuture(null);
        }
        return this.players.forget(player.uuid(), address, actor.asActor(), this.clock.instant())
                .thenAccept(removed -> this.runner.send(actor, removed == 0
                                ? MessageKey.UNLINK_NONE
                                : MessageKey.UNLINK_DONE,
                        Placeholder.unparsed("name", name),
                        Placeholder.unparsed("count", Integer.toString(removed))));
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
        return this.players.lastUuid(name).thenAccept(uuid -> uuid.ifPresentOrElse(
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
