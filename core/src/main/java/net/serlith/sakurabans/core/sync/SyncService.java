package net.serlith.sakurabans.core.sync;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;

public final class SyncService {

    private final SyncTransport transport;
    private final ComponentLogger logger;

    public SyncService(final SyncTransport transport, final ComponentLogger logger) {
        this.transport = transport;
        this.logger = logger;
    }

    public void publish(final SyncPacket packet) {
        try {
            this.transport.send(packet);
        } catch (final RuntimeException e) {
            this.logger.error("Failed to publish {}", packet, e);
        }
    }

    public void start(final SyncPacketHandler handler) {
        this.transport.start(handler);
    }

    public void stop() {
        this.transport.stop();
    }

    public CompletableFuture<Integer> cleanup(final Instant now) {
        return this.transport.cleanup(now);
    }
}
