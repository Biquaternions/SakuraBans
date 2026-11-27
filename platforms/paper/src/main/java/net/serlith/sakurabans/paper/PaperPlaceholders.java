package net.serlith.sakurabans.paper;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.serlith.sakurabans.core.SakuraBansCore;
import net.serlith.sakurabans.core.placeholder.MiniPlaceholdersBridge;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.plugin.PluginManager;

final class PaperPlaceholders implements Listener {

    static final String PLACEHOLDER_API = "PlaceholderAPI";
    static final String MINI_PLACEHOLDERS = "MiniPlaceholders";

    private final SakuraBansCore core;
    private final PluginManager plugins;
    private final Set<String> started = ConcurrentHashMap.newKeySet();

    PaperPlaceholders(final SakuraBansCore core, final PluginManager plugins) {
        this.core = core;
        this.plugins = plugins;
    }

    void start() {
        for (final String plugin : Set.of(PLACEHOLDER_API, MINI_PLACEHOLDERS)) {
            if (this.plugins.isPluginEnabled(plugin)) {
                this.enabled(plugin);
            }
        }
    }

    @EventHandler
    public void onPluginEnable(final PluginEnableEvent event) {
        this.enabled(event.getPlugin().getName());
    }

    private void enabled(final String plugin) {
        if (!plugin.equals(PLACEHOLDER_API) && !plugin.equals(MINI_PLACEHOLDERS)
                || !this.started.add(plugin)) {
            return;
        }
        // Lambdas, so a bridge class is resolved only when its plugin is there.
        if (plugin.equals(PLACEHOLDER_API)) {
            this.core.placeholderBridges().start(plugin, true,
                    () -> PaperPlaceholderExpansion.register(this.core.placeholders()));
        } else {
            this.core.placeholderBridges().start(plugin, true,
                    () -> MiniPlaceholdersBridge.register(this.core.placeholders()));
        }
    }
}
