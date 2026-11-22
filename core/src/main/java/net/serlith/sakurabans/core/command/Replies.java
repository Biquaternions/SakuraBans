package net.serlith.sakurabans.core.command;

import java.time.InstantSource;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.serlith.sakurabans.api.model.Punishment;
import net.serlith.sakurabans.api.model.Victim;
import net.serlith.sakurabans.core.configuration.ConfigService;
import net.serlith.sakurabans.core.message.Placeholders;
import net.serlith.sakurabans.core.permission.Permissions;
import net.serlith.sakurabans.core.player.NameCache;

public final class Replies {

    private final CommandRunner runner;
    private final Placeholders placeholders;
    private final NameCache names;
    private final ConfigService config;
    private final InstantSource clock;

    public Replies(final CommandRunner runner, final Placeholders placeholders,
            final NameCache names, final ConfigService config, final InstantSource clock) {
        this.runner = runner;
        this.placeholders = placeholders;
        this.names = names;
        this.config = config;
        this.clock = clock;
    }

    public TagResolver punishment(final CommandActor actor, final Punishment punishment) {
        return this.placeholders.punishment(punishment, this.runner.locale(actor),
                this.clock.instant(), this.names::name, this.showAddresses(actor));
    }

    public String victim(final CommandActor actor, final Victim victim) {
        return this.placeholders.victim(victim, this.names::name, this.showAddresses(actor));
    }

    public boolean showAddresses(final CommandActor actor) {
        return actor.console() || !this.config.settings().display().censorIpAddresses()
                || actor.hasPermission(Permissions.VIEW_IPS);
    }

}
