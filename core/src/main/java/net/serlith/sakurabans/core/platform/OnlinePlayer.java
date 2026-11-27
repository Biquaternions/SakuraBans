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

    default void disconnectFromNetwork(final Component reason) {
        this.disconnect(reason);
    }

    boolean showNotice(Component title, Component body, Component button);

    CompletableFuture<Boolean> connect(String server);
}
