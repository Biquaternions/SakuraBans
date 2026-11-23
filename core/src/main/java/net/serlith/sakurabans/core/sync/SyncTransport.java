package net.serlith.sakurabans.core.sync;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;

public interface SyncTransport {

    /** A single instance (or sync off): nothing to tell. */
    SyncTransport NONE = packet -> { };

    void send(SyncPacket packet);

    default void start(final SyncPacketHandler handler) {
    }

    default void stop() {
    }

    default CompletableFuture<Integer> cleanup(final Instant now) {
        return CompletableFuture.completedFuture(0);
    }

    static SyncTransport enabledWhen(final BooleanSupplier enabled,
            final SyncTransport transport) {
        return new SyncTransport() {
            @Override
            public void send(final SyncPacket packet) {
                if (enabled.getAsBoolean()) {
                    transport.send(packet);
                }
            }

            @Override
            public void start(final SyncPacketHandler handler) {
                if (enabled.getAsBoolean()) {
                    transport.start(handler);
                }
            }

            @Override
            public void stop() {
                transport.stop();
            }

            @Override
            public CompletableFuture<Integer> cleanup(final Instant now) {
                return enabled.getAsBoolean()
                        ? transport.cleanup(now)
                        : CompletableFuture.completedFuture(0);
            }
        };
    }

}
