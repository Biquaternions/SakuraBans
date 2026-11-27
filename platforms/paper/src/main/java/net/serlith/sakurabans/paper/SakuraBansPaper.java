package net.serlith.sakurabans.paper;

import net.serlith.sakurabans.api.SakuraBansApi;
import net.serlith.sakurabans.core.SakuraBansCore;
import net.serlith.sakurabans.core.command.CommandActor;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.incendo.cloud.paper.PaperCommandManager;

public final class SakuraBansPaper extends JavaPlugin {

    private final SakuraBansCore core;
    private final PaperCommandManager.Bootstrapped<CommandActor> commands;

    SakuraBansPaper(final SakuraBansCore core,
            final PaperCommandManager.Bootstrapped<CommandActor> commands) {
        this.core = core;
        this.commands = commands;
    }

    @Override
    public void onEnable() {
        this.commands.onEnable();
        this.getServer().getMessenger().registerOutgoingPluginChannel(this,
                PaperNetworkKick.CHANNEL);
        final PaperPlatform platform = new PaperPlatform(this);
        this.core.start(platform);
        this.getServer().getPluginManager()
                .registerEvents(new PaperLoginListener(this.core.loginGate()), this);
        this.getServer().getPluginManager()
                .registerEvents(new PaperChatListener(this.core.chatGate(), platform), this);
        this.getServer().getServicesManager().register(SakuraBansApi.class, this.core.api(),
                this, ServicePriority.Normal);
        final PaperPlaceholders placeholders =
                new PaperPlaceholders(this.core, this.getServer().getPluginManager());
        this.getServer().getPluginManager().registerEvents(placeholders, this);
        placeholders.start();
    }

    @Override
    public void onDisable() {
        this.getServer().getServicesManager().unregisterAll(this);
        this.core.stop();
    }
}
