package net.serlith.sakurabans.fidorial;

import fr.fidorial.plugin.PluginContext;
import lombok.RequiredArgsConstructor;
import net.kyori.adventure.audience.Audience;
import net.serlith.sakurabans.core.platform.*;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

@RequiredArgsConstructor
public class FidorialPlatform implements Platform {

    private final PluginContext ctx;
    private final FidorialTaskScheduler scheduler;
    private final ServerIdentity identity;

    FidorialPlatform(final PluginContext ctx) {
        this.ctx = ctx;
        this.identity = new FidorialServerIdentity();
        this.scheduler = new FidorialTaskScheduler(ctx);
    }

    @Override
    public TaskScheduler scheduler() {
        return this.scheduler;
    }

    @Override
    public Optional<OnlinePlayer> player(UUID uuid) {
        return this.ctx.server().player(uuid)
                .map(player -> new FidorialOnlinePlayer(player, this.scheduler));
    }

    @Override
    public Collection<? extends OnlinePlayer> onlinePlayers() {
        return this.ctx.server().onlinePlayers().stream()
                .map(player -> new FidorialOnlinePlayer(player, this.scheduler))
                .toList();
    }

    @Override
    public Audience console() {
        return null;
    }

    @Override
    public void dispatchConsoleCommand(String command) {

    }

    @Override
    public ServerIdentity serverIdentity() {
        return this.identity;
    }

    @Override
    public ProfileLookup profiles() {
        return ProfileLookup.NONE;
    }

    @Override
    public boolean onlineMode() {
        return true;
    }

    @Override
    public String platformName() {
        return "fidorial";
    }

    @Override
    public String platformVersion() {
        return this.ctx.server().minecraftVersion();
    }

}
