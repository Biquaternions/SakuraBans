package net.serlith.sakurabans.core.event;

import com.google.common.base.Preconditions;
import java.net.InetAddress;
import java.util.Optional;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.serlith.sakurabans.api.event.PlayerDenyEvent;
import net.serlith.sakurabans.api.model.Punishment;
import org.jspecify.annotations.Nullable;

public final class SakuraPlayerDenyEvent implements PlayerDenyEvent {

    private final UUID uuid;
    private final String name;
    private final InetAddress address;
    private final Denial denial;
    private final @Nullable Punishment punishment;
    private Component message;
    private boolean cancelled;

    public SakuraPlayerDenyEvent(final UUID uuid, final String name, final InetAddress address,
            final Denial denial, final @Nullable Punishment punishment, final Component message) {
        this.uuid = uuid;
        this.name = name;
        this.address = address;
        this.denial = denial;
        this.punishment = punishment;
        this.message = message;
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
    public Denial denial() {
        return this.denial;
    }

    @Override
    public Optional<Punishment> punishment() {
        return Optional.ofNullable(this.punishment);
    }

    @Override
    public Component message() {
        return this.message;
    }

    @Override
    public void message(final Component message) {
        this.message = Preconditions.checkNotNull(message, "message");
    }

    @Override
    public boolean cancelled() {
        return this.cancelled;
    }

    @Override
    public void cancelled(final boolean cancelled) {
        this.cancelled = cancelled;
    }

}
