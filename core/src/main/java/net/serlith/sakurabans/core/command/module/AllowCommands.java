package net.serlith.sakurabans.core.command.module;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.serlith.sakurabans.api.allow.AllowEntry;
import net.serlith.sakurabans.api.allow.AllowedAddress;
import net.serlith.sakurabans.api.allow.AllowedPlayer;
import net.serlith.sakurabans.api.model.AddressVictim;
import net.serlith.sakurabans.core.alts.SakuraAllowListService;
import net.serlith.sakurabans.core.command.CommandActor;
import net.serlith.sakurabans.core.command.CommandModule;
import net.serlith.sakurabans.core.command.CommandRegistrar;
import net.serlith.sakurabans.core.command.CommandRunner;
import net.serlith.sakurabans.core.command.Replies;
import net.serlith.sakurabans.core.command.SakuraCommands;
import net.serlith.sakurabans.core.command.parser.TargetParser;
import net.serlith.sakurabans.core.message.Formats;
import net.serlith.sakurabans.core.message.MessageKey;
import net.serlith.sakurabans.core.message.Placeholders;
import net.serlith.sakurabans.core.model.ResolvedTarget;
import net.serlith.sakurabans.core.permission.Permissions;
import net.serlith.sakurabans.core.player.NameCache;
import net.serlith.sakurabans.core.player.TargetResolver;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.parser.standard.StringParser;

public final class AllowCommands implements CommandModule {

    private final CommandRegistrar registrar;
    private final CommandRunner runner;
    private final Replies replies;
    private final Formats formats;
    private final TargetParser targetParser;
    private final TargetResolver targets;
    private final SakuraAllowListService allowList;
    private final NameCache names;
    private final Placeholders placeholders;

    public AllowCommands(final CommandRegistrar registrar, final CommandRunner runner,
            final Replies replies, final Formats formats, final TargetParser targetParser,
            final TargetResolver targets, final SakuraAllowListService allowList,
            final NameCache names, final Placeholders placeholders) {
        this.registrar = registrar;
        this.runner = runner;
        this.replies = replies;
        this.formats = formats;
        this.targetParser = targetParser;
        this.targets = targets;
        this.allowList = allowList;
        this.names = names;
        this.placeholders = placeholders;
    }

    @Override
    public void register(final CommandManager<CommandActor> manager) {
        this.registrar.builder(manager, SakuraCommands.ROOT, MessageKey.COMMAND_DESCRIPTION_ALLOW)
                .ifPresent(root -> {
                    final var allow = root.literal("allow").permission(Permissions.ADMIN_ALLOW);
                    manager.command(allow.literal("add")
                            .required("target", this.targetParser.descriptor())
                            .optional("note", StringParser.greedyStringParser())
                            .futureHandler(context -> this.runner.run(context.sender(),
                                    () -> this.change(context, true))));
                    manager.command(allow.literal("remove")
                            .required("target", this.targetParser.descriptor())
                            .futureHandler(context -> this.runner.run(context.sender(),
                                    () -> this.change(context, false))));
                    manager.command(allow.literal("list")
                            .handler(context -> this.list(context.sender())));
                });
    }

    private CompletableFuture<?> change(final CommandContext<CommandActor> context,
            final boolean add) {
        final CommandActor actor = context.sender();
        final String note = context.<String>optional("note").orElse(null);
        return this.targets.resolve(context.get("target")).thenCompose(target -> {
            final String shown;
            final CompletableFuture<Boolean> changed;
            switch (target) {
                case ResolvedTarget.Player player -> {
                    shown = PunishCommands.displayName(player);
                    changed = add
                            ? this.allowList.allow(player.uuid(), actor.asActor(), note)
                            : this.allowList.disallow(player.uuid(), actor.asActor());
                }
                case ResolvedTarget.Address address -> {
                    shown = this.replies.victim(actor, AddressVictim.of(address.address()));
                    changed = add
                            ? this.allowList.allow(address.address(), actor.asActor(), note)
                            : this.allowList.disallow(address.address(), actor.asActor());
                }
                default -> {
                    this.runner.send(actor, MessageKey.ALLOW_TARGET);
                    return CompletableFuture.completedFuture(null);
                }
            }
            return changed.thenAccept(done -> this.runner.send(actor, add
                    ? done ? MessageKey.ALLOW_ADDED : MessageKey.ALLOW_ALREADY
                    : done ? MessageKey.ALLOW_REMOVED : MessageKey.ALLOW_NOT_LISTED,
                    Placeholder.unparsed("target", shown)));
        });
    }

    private void list(final CommandActor actor) {
        final List<AllowEntry> entries = this.allowList.entries();
        if (entries.isEmpty()) {
            this.runner.send(actor, MessageKey.ALLOW_LIST_EMPTY);
            return;
        }
        this.runner.send(actor, MessageKey.ALLOW_LIST_HEADER,
                Placeholder.unparsed("count", Integer.toString(entries.size())));
        for (final AllowEntry entry : entries) {
            final String target = switch (entry) {
                case AllowedPlayer player -> this.names.name(player.uuid())
                        .orElse(player.uuid().toString());
                case AllowedAddress address ->
                        this.replies.victim(actor, AddressVictim.of(address.address()));
            };
            this.runner.send(actor, MessageKey.ALLOW_LIST_ENTRY,
                    Placeholder.unparsed("target", target),
                    Placeholder.unparsed("operator", this.placeholders.operator(
                            entry.addedBy().operator(), this.runner.locale(actor))),
                    Placeholder.unparsed("added", this.formats.instant(entry.addedAt(),
                            this.runner.locale(actor))),
                    Placeholder.unparsed("note", entry.note().orElse("-")));
        }
    }

}
