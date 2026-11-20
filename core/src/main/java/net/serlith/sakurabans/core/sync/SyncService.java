package net.serlith.sakurabans.core.sync;

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

}
