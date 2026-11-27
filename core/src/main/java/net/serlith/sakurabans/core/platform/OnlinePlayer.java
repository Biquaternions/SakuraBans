package net.serlith.sakurabans.core.platform;

import java.net.InetAddress;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;

public interface OnlinePlayer extends Audience {

    UUID uuid();

    String name();

    InetAddress address();

    Optional<String> currentServer();

    boolean hasPermission(String node);

    Optional<Locale> locale();

    void disconnect(Component reason);

    /**
     * Shows a modal notice (title, body, a button that closes it), hopping to the player's
     * thread if needed. Returns {@code false}, without showing anything, when this player can't
     * be shown one (the platform has no dialogs, the client is too old, the proxy lacks
     * packetevents); the caller then sends the body as chat.
     */
    boolean showNotice(Component title, Component body, Component button);

    CompletableFuture<Boolean> connect(String server);
}
