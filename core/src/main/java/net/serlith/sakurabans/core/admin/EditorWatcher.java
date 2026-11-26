package net.serlith.sakurabans.core.admin;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import net.serlith.sakurabans.api.model.Actor;
import net.serlith.sakurabans.api.model.ConsoleOperator;
import net.serlith.sakurabans.api.model.Operator;
import net.serlith.sakurabans.api.model.PlayerOperator;
import net.serlith.sakurabans.core.concurrent.SakuraExecutors;
import net.serlith.sakurabans.core.configuration.ConfigService;
import net.serlith.sakurabans.core.configuration.settings.Settings;
import net.serlith.sakurabans.core.message.MessageKey;
import net.serlith.sakurabans.core.permission.Permissions;
import net.serlith.sakurabans.core.platform.OnlinePlayer;
import net.serlith.sakurabans.core.platform.Platform;
import org.jspecify.annotations.Nullable;

/**
 * Live apply (protocol section 9): the creating instance holds the session's socket and applies
 * each saved change set with the creator as the actor. One watched session per sender.
 */
public final class EditorWatcher {

    /** How often the socket is pinged, the stop rules are checked and drops are retried. */
    public record Timing(Duration ping, Duration check, Duration browserGrace,
            List<Duration> retries) {

        public static final Timing DEFAULT = new Timing(Duration.ofSeconds(20),
                Duration.ofSeconds(5), Duration.ofMinutes(2), List.of(Duration.ofSeconds(2),
                        Duration.ofSeconds(5), Duration.ofSeconds(15), Duration.ofSeconds(30)));

        public Timing {
            retries = List.copyOf(retries);
        }
    }

    private static final String PING = "{\"type\":\"ping\"}";

    private final ConfigService config;
    private final EditorService editor;
    private final EditorSocket.Connector connector;
    private final EditorLines lines;
    private final Platform platform;
    private final SakuraExecutors executors;
    private final Timing timing;
    private final InstantSource clock;
    private final ComponentLogger logger;
    private final Map<String, Watch> watches = new ConcurrentHashMap<>();
    private final AtomicReference<@Nullable ScheduledFuture<?>> checks = new AtomicReference<>();

    public EditorWatcher(final ConfigService config, final EditorService editor,
            final EditorSocket.Connector connector, final EditorLines lines,
            final Platform platform, final SakuraExecutors executors, final Timing timing,
            final InstantSource clock, final ComponentLogger logger) {
        this.config = config;
        this.editor = editor;
        this.connector = connector;
        this.lines = lines;
        this.platform = platform;
        this.executors = executors;
        this.timing = timing;
        this.clock = clock;
        this.logger = logger;
    }

    public void start() {
        final ScheduledFuture<?> previous = this.checks.getAndSet(this.executors.repeat(
                this.timing.check(), this.timing.check(), this::check));
        if (previous != null) {
            previous.cancel(false);
        }
    }

    public void stop() {
        final ScheduledFuture<?> current = this.checks.getAndSet(null);
        if (current != null) {
            current.cancel(false);
        }
        List.copyOf(this.watches.values()).forEach(Watch::stop);
    }

    /**
     * Starts watching a session the sender just opened, replacing the sender's older one. False
     * when {@code web.live-apply} is off.
     */
    public boolean watch(final String session, final String token, final Operator creator,
            final Instant createdAt) {
        final Settings.Web web = this.config.settings().web();
        if (!web.liveApply() || web.url() == null) {
            return false;
        }
        final Watch watch = new Watch(session, token, creator, createdAt, web);
        final Watch previous = this.watches.put(key(creator), watch);
        if (previous != null) {
            previous.stop();
        }
        watch.connect();
        return true;
    }

    public Optional<String> watched(final Operator creator) {
        return Optional.ofNullable(this.watches.get(key(creator))).map(watch -> watch.session);
    }

    /** Completes once the notices received so far are handled. */
    public CompletableFuture<Void> idle() {
        return CompletableFuture.allOf(this.watches.values().stream()
                .map(watch -> watch.queued()).toArray(CompletableFuture[]::new));
    }

    /** Applies the stop rules that time, presence and settings decide. */
    public void check() {
        List.copyOf(this.watches.values()).forEach(Watch::check);
    }

    private static String key(final Operator creator) {
        return switch (creator) {
            case PlayerOperator player -> "player:" + player.uuid();
            case ConsoleOperator console -> "console";
            default -> "other:" + creator;
        };
    }

    private final class Watch implements EditorSocket.Listener {

        private final String session;
        private final String token;
        private final Operator creator;
        private final Instant createdAt;
        private final Settings.Web web;
        /** Notices of this session, handled one after another on the worker. */
        private CompletableFuture<Void> queue = CompletableFuture.completedFuture(null);
        private @Nullable EditorSocket socket;
        private @Nullable ScheduledFuture<?> pings;
        private @Nullable ScheduledFuture<?> retry;
        private int browsers;
        private boolean browserSeen;
        private @Nullable Instant browserlessSince;
        private int attempts;
        private volatile boolean stopped;

        private Watch(final String session, final String token, final Operator creator,
                final Instant createdAt, final Settings.Web web) {
            this.session = session;
            this.token = token;
            this.creator = creator;
            this.createdAt = createdAt;
            this.web = web;
        }

        private synchronized void connect() {
            if (this.stopped) {
                return;
            }
            EditorWatcher.this.connector.connect(this.session, this.token, this)
                    .whenComplete((socket, failure) -> this.connected(socket, failure));
        }

        private synchronized void connected(final @Nullable EditorSocket socket,
                final @Nullable Throwable failure) {
            if (socket == null) {
                final Throwable cause = failure instanceof CompletionException
                        && failure.getCause() != null ? failure.getCause() : failure;
                if (cause instanceof EditorSocket.Refused refused && refused.permanent()) {
                    EditorWatcher.this.logger.debug("The editor site refused the socket of"
                            + " session {} ({}): watching stops", this.session, refused.status());
                    this.stop();
                    return;
                }
                this.dropped(EditorSocket.ABNORMAL, String.valueOf(cause));
                return;
            }
            if (this.stopped) {
                socket.close(EditorSocket.NORMAL, "stopped");
                return;
            }
            this.socket = socket;
            this.pings = EditorWatcher.this.executors.repeat(EditorWatcher.this.timing.ping(),
                    EditorWatcher.this.timing.ping(), () -> this.send(PING));
        }

        private synchronized void send(final String text) {
            if (this.socket != null && !this.stopped) {
                this.socket.send(text);
            }
        }

        @Override
        public void text(final String text) {
            this.enqueue(() -> this.frame(text));
        }

        @Override
        public void closed(final int code, final String reason) {
            this.enqueue(() -> {
                this.dropped(code, reason);
                return CompletableFuture.completedFuture(null);
            });
        }

        private synchronized CompletableFuture<Void> queued() {
            return this.queue;
        }

        /** Hands work from the socket's threads to the worker, in arrival order. */
        private void enqueue(final Supplier<CompletableFuture<Void>> work) {
            synchronized (this) {
                this.queue = this.queue.thenComposeAsync(ignored -> work.get(),
                                EditorWatcher.this.executors.worker())
                        .exceptionally(failure -> {
                            EditorWatcher.this.logger.warn("Live apply of editor session {}"
                                    + " failed", this.session, failure);
                            return null;
                        });
            }
        }

        private CompletableFuture<Void> frame(final String text) {
            final JsonObject frame;
            try {
                final JsonElement json = JsonParser.parseString(text);
                if (!json.isJsonObject()) {
                    return CompletableFuture.completedFuture(null);
                }
                frame = json.getAsJsonObject();
            } catch (final JsonParseException e) {
                return CompletableFuture.completedFuture(null);
            }
            final String type = string(frame, "type");
            switch (type == null ? "" : type) {
                case "hello" -> {
                    synchronized (this) {
                        this.attempts = 0;
                    }
                    this.browsers(number(frame, "browsers"));
                    CompletableFuture<Void> applied = CompletableFuture.completedFuture(null);
                    final JsonElement pending = frame.get("pending");
                    if (pending != null && pending.isJsonArray()) {
                        for (final JsonElement code : pending.getAsJsonArray()) {
                            if (code.isJsonPrimitive()) {
                                final String value = code.getAsString();
                                applied = applied.thenCompose(ignored -> this.apply(value));
                            }
                        }
                    }
                    return applied;
                }
                case "browsers" -> this.browsers(number(frame, "count"));
                case "changes" -> {
                    final String code = string(frame, "code");
                    if (code != null) {
                        return this.apply(code);
                    }
                }
                default -> {
                    // pong and frames of newer sites
                }
            }
            return CompletableFuture.completedFuture(null);
        }

        private synchronized void browsers(final int count) {
            this.browsers = Math.max(0, count);
            if (this.browsers > 0) {
                this.browserSeen = true;
                this.browserlessSince = null;
            } else if (this.browserlessSince == null) {
                this.browserlessSince = EditorWatcher.this.clock.instant();
            }
        }

        private CompletableFuture<Void> apply(final String code) {
            if (this.stopped || !EditorClient.validCode(code)) {
                return CompletableFuture.completedFuture(null);
            }
            final Set<EditorService.Section> allowed = this.allowed().orElse(null);
            if (allowed == null) {
                this.stop();
                return CompletableFuture.completedFuture(null);
            }
            return EditorWatcher.this.editor.apply(code, Actor.of(this.creator,
                            EditorService.SOURCE), allowed, EditorService.By.LIVE)
                    .thenAccept(application -> {
                        if (application.applied() instanceof EditorService.Applied.Replayed) {
                            // Applied by a command meanwhile: its sender was told.
                            return;
                        }
                        EditorWatcher.this.lines.toCreator(this.creator, application.applied(),
                                MessageKey.EDITOR_APPLIED_LIVE);
                    });
        }

        /** What the creator may change now; empty when the creator is gone. */
        private Optional<Set<EditorService.Section>> allowed() {
            if (this.creator instanceof ConsoleOperator) {
                return Optional.of(EnumSet.allOf(EditorService.Section.class));
            }
            if (!(this.creator instanceof PlayerOperator player)) {
                return Optional.empty();
            }
            final OnlinePlayer online =
                    EditorWatcher.this.platform.player(player.uuid()).orElse(null);
            if (online == null) {
                return Optional.empty();
            }
            return Optional.of(online.hasPermission(Permissions.EDITOR)
                    ? EditorService.allowed(online::hasPermission)
                    : EnumSet.noneOf(EditorService.Section.class));
        }

        private synchronized void dropped(final int code, final String reason) {
            this.socket = null;
            this.cancel(this.pings);
            this.pings = null;
            if (this.stopped) {
                return;
            }
            if (code == EditorSocket.REPLACED || code == EditorSocket.EXPIRED) {
                EditorWatcher.this.logger.debug("Editor session {} closed ({}): watching"
                        + " stops", this.session, code);
                this.stop();
                return;
            }
            final List<Duration> retries = EditorWatcher.this.timing.retries();
            final Duration delay = retries.get(Math.min(this.attempts, retries.size() - 1));
            if (this.attempts == 0) {
                EditorWatcher.this.logger.warn("The editor site's socket for session {} dropped"
                        + " ({} {}); retrying", this.session, code, reason);
            } else {
                EditorWatcher.this.logger.debug("The editor site's socket for session {}"
                        + " dropped again ({} {})", this.session, code, reason);
            }
            this.attempts++;
            this.retry = EditorWatcher.this.executors.schedule(delay, () -> {
                if (!this.check()) {
                    this.connect();
                }
            });
        }

        /** Stops when a rule says so; true when stopped. */
        private synchronized boolean check() {
            if (this.stopped) {
                return true;
            }
            final Instant now = EditorWatcher.this.clock.instant();
            final Duration grace = EditorWatcher.this.timing.browserGrace();
            final Settings.Web current = EditorWatcher.this.config.settings().web();
            final boolean stop = !current.liveApply()
                    || !Objects.equals(current.url(), this.web.url())
                    || !Objects.equals(current.token(), this.web.token())
                    || !now.isBefore(this.createdAt.plus(EditorService.SESSION_LIFETIME))
                    || !this.browserSeen && !now.isBefore(this.createdAt.plus(grace))
                    || this.browsers == 0 && this.browserlessSince != null
                            && !now.isBefore(this.browserlessSince.plus(grace))
                    || this.allowed().isEmpty();
            if (stop) {
                this.stop();
            }
            return stop;
        }

        private synchronized void stop() {
            if (this.stopped) {
                return;
            }
            this.stopped = true;
            EditorWatcher.this.watches.remove(key(this.creator), this);
            this.cancel(this.pings);
            this.cancel(this.retry);
            if (this.socket != null) {
                this.socket.close(EditorSocket.NORMAL, "done");
                this.socket = null;
            }
        }

        private void cancel(final @Nullable ScheduledFuture<?> task) {
            if (task != null) {
                task.cancel(false);
            }
        }
    }

    private static @Nullable String string(final JsonObject frame, final String key) {
        final JsonElement value = frame.get(key);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
                ? value.getAsString() : null;
    }

    private static int number(final JsonObject frame, final String key) {
        final JsonElement value = frame.get(key);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()
                ? value.getAsInt() : 0;
    }
}
