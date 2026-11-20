package net.serlith.sakurabans.core.sync;

public interface SyncTransport {

    /** Until S27 brings SQL polling: a single instance, nothing to tell. */
    SyncTransport NONE = packet -> { };

    void send(SyncPacket packet);
}
