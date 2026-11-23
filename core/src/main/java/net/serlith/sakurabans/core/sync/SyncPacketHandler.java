package net.serlith.sakurabans.core.sync;

import java.util.concurrent.CompletableFuture;

public interface SyncPacketHandler {

    CompletableFuture<Void> handle(SyncPacket packet);

    default CompletableFuture<Void> missed() {
        return CompletableFuture.completedFuture(null);
    }

}
