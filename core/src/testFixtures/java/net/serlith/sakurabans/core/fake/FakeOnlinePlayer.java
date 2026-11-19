package net.serlith.sakurabans.core.fake;

import java.net.InetAddress;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import net.kyori.adventure.text.Component;
import net.serlith.sakurabans.core.platform.OnlinePlayer;
import org.jspecify.annotations.Nullable;

public final class FakeOnlinePlayer implements OnlinePlayer {

    private final UUID uuid;
    private final String name;
    private final InetAddress address;
    private final @Nullable Locale locale;
    private final @Nullable String currentServer;
    private final Set<String> permissions = ConcurrentHashMap.newKeySet();
    private final List<Component> messages = new CopyOnWriteArrayList<>();
    private final AtomicReference<@Nullable Component> disconnectReason = new AtomicReference<>();
    private final FakePlatform platform;

    FakeOnlinePlayer(final FakePlatform platform, final UUID uuid, final String name,
            final InetAddress address, final @Nullable Locale locale,
            final @Nullable String currentServer) {
        this.platform = platform;
        this.uuid = uuid;
        this.name = name;
        this.address = address;
        this.locale = locale;
        this.currentServer = currentServer;
    }

    @Override
    public UUID uuid() {
        return this.uuid;
    }

    @Override
    public String name() {
        return this.name;
    }

    @Override
    public InetAddress address() {
        return this.address;
    }

    @Override
    public Optional<String> currentServer() {
        return Optional.ofNullable(this.currentServer);
    }

    @Override
    public boolean hasPermission(final String node) {
        return this.permissions.contains(node);
    }

    @Override
    public Optional<Locale> locale() {
        return Optional.ofNullable(this.locale);
    }

    @Override
    public void disconnect(final Component reason) {
        this.platform.scheduler().runForPlayer(this.uuid, () -> {
            this.disconnectReason.set(reason);
            this.platform.leave(this.uuid);
        });
    }

    @Override
    public void sendMessage(final Component message) {
        this.messages.add(message);
    }

    public FakeOnlinePlayer grant(final String... nodes) {
        this.permissions.addAll(List.of(nodes));
        return this;
    }

    public List<Component> messages() {
        return List.copyOf(this.messages);
    }

    public Optional<Component> disconnectReason() {
        return Optional.ofNullable(this.disconnectReason.get());
    }
}
