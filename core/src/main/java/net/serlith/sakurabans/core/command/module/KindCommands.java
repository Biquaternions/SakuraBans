package net.serlith.sakurabans.core.command.module;

import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.serlith.sakurabans.api.kind.AddressMode;
import net.serlith.sakurabans.api.kind.Kind;
import net.serlith.sakurabans.core.command.CommandActor;
import net.serlith.sakurabans.core.command.CommandModule;
import net.serlith.sakurabans.core.command.CommandRegistrar;
import net.serlith.sakurabans.core.command.CommandRunner;
import net.serlith.sakurabans.core.command.SakuraCommands;
import net.serlith.sakurabans.core.kind.SakuraKindService;
import net.serlith.sakurabans.core.message.MessageKey;
import net.serlith.sakurabans.core.message.Placeholders;
import net.serlith.sakurabans.core.permission.Permissions;
import net.serlith.sakurabans.core.scope.SakuraScopeService;
import org.incendo.cloud.CommandManager;

public final class KindCommands implements CommandModule {

    private final CommandRegistrar registrar;
    private final CommandRunner runner;
    private final SakuraKindService kinds;
    private final SakuraScopeService scopes;

    public KindCommands(final CommandRegistrar registrar, final CommandRunner runner,
            final SakuraKindService kinds, final SakuraScopeService scopes) {
        this.registrar = registrar;
        this.runner = runner;
        this.kinds = kinds;
        this.scopes = scopes;
    }

    @Override
    public void register(final CommandManager<CommandActor> manager) {
        this.registrar.builder(manager, SakuraCommands.ROOT, MessageKey.COMMAND_DESCRIPTION_KINDS)
                .ifPresent(root -> manager.command(root.literal("kinds")
                        .permission(Permissions.ADMIN_KINDS)
                        .literal("list")
                        .handler(context -> this.list(context.sender()))));
    }

    private void list(final CommandActor actor) {
        final List<Kind> all = this.kinds.kinds();
        this.runner.send(actor, MessageKey.KINDS_LIST_HEADER,
                Placeholder.unparsed("count", Integer.toString(all.size())));
        for (final Kind kind : all) {
            this.runner.send(actor, MessageKey.KINDS_LIST_ENTRY,
                    Placeholder.unparsed("id", kind.id()),
                    Placeholder.unparsed("name", kind.displayName()),
                    Placeholder.component("type", this.runner.render(actor,
                            Placeholders.typeKey(kind.baseType()))),
                    Placeholder.unparsed("modifiers", this.modifiers(kind)));
        }
    }

    private String modifiers(final Kind kind) {
        final List<String> modifiers = new ArrayList<>();
        if (kind.shadow()) {
            modifiers.add("shadow");
        }
        kind.strictness().ifPresent(strictness -> modifiers.add("strictness=" + strictness));
        kind.defaultScope().ifPresent(scope -> modifiers.add("scope=" + this.scopes.format(scope)));
        if (kind.addressMode() != AddressMode.AS_GIVEN) {
            modifiers.add("address=" + kind.addressMode());
        }
        if (kind.permanentOnly()) {
            modifiers.add("permanent-only");
        }
        if (!kind.requiredEvidence().isEmpty()) {
            modifiers.add("evidence=" + String.join(",", kind.requiredEvidence()));
        }
        return modifiers.isEmpty() ? "" : " [" + String.join(" ", modifiers) + "]";
    }

}
