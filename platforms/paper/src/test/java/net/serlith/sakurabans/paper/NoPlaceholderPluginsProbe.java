package net.serlith.sakurabans.paper;

import java.lang.reflect.Proxy;
import org.bukkit.plugin.PluginManager;

/** Starts the placeholder bridges as SakuraBansPaper does on a server with neither plugin. */
public final class NoPlaceholderPluginsProbe implements Runnable {

    @Override
    public void run() {
        final PluginManager plugins = (PluginManager) Proxy.newProxyInstance(
                PluginManager.class.getClassLoader(), new Class<?>[] {PluginManager.class},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("isPluginEnabled")) {
                        return false;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
        // The core is only used once a plugin is enabled.
        new PaperPlaceholders(null, plugins).start();
    }
}
