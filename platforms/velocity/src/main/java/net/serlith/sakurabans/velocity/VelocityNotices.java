package net.serlith.sakurabans.velocity;

import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;

/**
 * Dialogs on the proxy. Velocity has no API to build one, so they go through packetevents when
 * that plugin is loaded (S61); otherwise nothing is shown and the core falls back to chat.
 */
interface VelocityNotices {

    VelocityNotices NONE = (player, title, body, button) -> false;

    boolean show(Player player, Component title, Component body, Component button);

    // VelocityDialogBridge is the only class that touches packetevents. It is created at the
    // first dialog, inside the guard, so a proxy without packetevents never resolves its classes
    // and one with an incompatible packetevents gets chat instead of an error (S64).
    static VelocityNotices create(final boolean packetEventsLoaded, final ComponentLogger logger) {
        if (!packetEventsLoaded) {
            return NONE;
        }
        // A lambda, not a constructor reference, so the class is resolved only when it runs.
        return new GuardedNotices(() -> new VelocityDialogBridge(), failure -> logger.warn(
                "Dialogs through packetevents failed; sending template messages as chat",
                failure));
    }
}
