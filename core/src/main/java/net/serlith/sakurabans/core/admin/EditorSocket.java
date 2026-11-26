package net.serlith.sakurabans.core.admin;

import java.util.concurrent.CompletableFuture;

/**
 * The plugin's side of a session's socket (protocol section 9), as narrow as the watcher needs:
 * the network boundary the tests fake.
 */
public interface EditorSocket {

    /** Close code of a socket replaced by a newer one for the same session. */
    int REPLACED = 4000;
    /** Close code of a session the site doesn't know (any more). */
    int EXPIRED = 4004;
    int NORMAL = 1000;
    /** What {@link Listener#closed} gets when the connection failed without a close frame. */
    int ABNORMAL = 1006;

    /** The site refused the upgrade with a plain HTTP status, before any socket opened. */
    final class Refused extends Exception {

        private static final long serialVersionUID = 1L;

        private final int status;

        public Refused(final int status) {
            super("the editor site refused the socket with " + status);
            this.status = status;
        }

        public int status() {
            return this.status;
        }

        /**
         * Unknown or expired session (404), wrong session token (401) or a bad request (400):
         * trying again can't help. Anything else (429, 503, ...) is a drop.
         */
        public boolean permanent() {
            return this.status == 400 || this.status == 401 || this.status == 404;
        }
    }

    void send(String text);

    void close(int code, String reason);

    /** Called on the network's threads; implementations hand the work on and return. */
    interface Listener {

        /** One whole text frame. */
        void text(String text);

        void closed(int code, String reason);
    }

    @FunctionalInterface
    interface Connector {

        CompletableFuture<EditorSocket> connect(String session,
                String token, Listener listener);
    }
}
