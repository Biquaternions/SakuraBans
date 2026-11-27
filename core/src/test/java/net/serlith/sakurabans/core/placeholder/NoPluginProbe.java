package net.serlith.sakurabans.core.placeholder;

import net.serlith.sakurabans.core.RecordingLogger;

/** Starts the bridges the way a platform does when neither plugin is installed. */
public final class NoPluginProbe implements Runnable {

    @Override
    public void run() {
        final PlaceholderBridges bridges = new PlaceholderBridges(new RecordingLogger().logger());
        final PlaceholderValues values = null;
        if (bridges.start("MiniPlaceholders", false,
                () -> MiniPlaceholdersBridge.register(values))) {
            throw new IllegalStateException("started without the plugin");
        }
        bridges.stop();
    }
}
