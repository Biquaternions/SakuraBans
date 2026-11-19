package net.serlith.sakurabans.core.platform;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import net.kyori.adventure.audience.Audience;
import org.jspecify.annotations.Nullable;

public final class LatePlatform implements Platform {

    private final AtomicReference<@Nullable Platform> delegate = new AtomicReference<>();

    public void bind(final Platform platform) {
        if (!this.delegate.compareAndSet(null, platform)) {
            throw new IllegalStateException("the platform is already bound");
        }
    }

    public boolean bound() {
        return this.delegate.get() != null;
    }

    private Platform platform() {
        final Platform platform = this.delegate.get();
        if (platform == null) {
            throw new IllegalStateException("the platform isn't available before start");
        }
        return platform;
    }

    @Override
    public TaskScheduler scheduler() {
        return this.platform().scheduler();
    }

    @Override
    public Optional<OnlinePlayer> player(final UUID uuid) {
        return this.platform().player(uuid);
    }

    @Override
    public Collection<? extends OnlinePlayer> onlinePlayers() {
        return this.platform().onlinePlayers();
    }

    @Override
    public Audience console() {
        return this.platform().console();
    }

    @Override
    public void dispatchConsoleCommand(final String command) {
        this.platform().dispatchConsoleCommand(command);
    }

    @Override
    public ServerIdentity serverIdentity() {
        return this.platform().serverIdentity();
    }

    @Override
    public boolean onlineMode() {
        return this.platform().onlineMode();
    }

    @Override
    public String platformVersion() {
        return this.platform().platformVersion();
    }
}
