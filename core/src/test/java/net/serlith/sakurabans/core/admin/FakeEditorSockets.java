package net.serlith.sakurabans.core.admin;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** The site's socket end, in memory: the watcher's network seam (protocol section 9). */
public final class FakeEditorSockets implements EditorSocket.Connector {

    /** One connection the watcher made. */
    public static final class Connection implements EditorSocket {

        private final String session;
        private final String token;
        private final EditorSocket.Listener listener;
        private final List<String> sent = new CopyOnWriteArrayList<>();
        private final AtomicInteger closedWith = new AtomicInteger();

        private Connection(final String session, final String token,
                final EditorSocket.Listener listener) {
            this.session = session;
            this.token = token;
            this.listener = listener;
        }

        public String session() {
            return this.session;
        }

        public String token() {
            return this.token;
        }

        public List<String> sent() {
            return List.copyOf(this.sent);
        }

        /** The code the plugin closed it with, 0 while open. */
        public int closedWith() {
            return this.closedWith.get();
        }

        /** A frame from the site. */
        public void frame(final String json) {
            this.listener.text(json);
        }

        /** The site closes or the connection drops. */
        public void drop(final int code) {
            this.listener.closed(code, "test");
        }

        @Override
        public void send(final String text) {
            this.sent.add(text);
        }

        @Override
        public void close(final int code, final String reason) {
            this.closedWith.compareAndSet(0, code);
        }
    }

    private final List<Connection> connections = new CopyOnWriteArrayList<>();
    private final AtomicBoolean refusing = new AtomicBoolean();
    private final AtomicInteger refusedWith = new AtomicInteger();
    private final AtomicInteger attempts = new AtomicInteger();

    /** Upgrades are refused with this HTTP status while it isn't 0. */
    public FakeEditorSockets refuseWith(final int status) {
        this.refusedWith.set(status);
        return this;
    }

    public int attempts() {
        return this.attempts.get();
    }

    /** Connection attempts fail while true, as with the site down. */
    public FakeEditorSockets refusing(final boolean refusing) {
        this.refusing.set(refusing);
        return this;
    }

    public List<Connection> connections() {
        return List.copyOf(this.connections);
    }

    public Connection last() {
        return this.connections.getLast();
    }

    @Override
    public CompletableFuture<EditorSocket> connect(final String session, final String token,
            final EditorSocket.Listener listener) {
        this.attempts.incrementAndGet();
        if (this.refusedWith.get() != 0) {
            return CompletableFuture.failedFuture(
                    new EditorSocket.Refused(this.refusedWith.get()));
        }
        if (this.refusing.get()) {
            return CompletableFuture.failedFuture(new IOException("refused"));
        }
        final Connection connection = new Connection(session, token, listener);
        this.connections.add(connection);
        return CompletableFuture.completedFuture(connection);
    }
}
