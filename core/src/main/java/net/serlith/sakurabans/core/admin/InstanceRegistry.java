package net.serlith.sakurabans.core.admin;

import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import net.serlith.sakurabans.core.concurrent.SakuraExecutors;
import net.serlith.sakurabans.core.configuration.ConfigService;
import net.serlith.sakurabans.core.configuration.SettingsSchema;
import net.serlith.sakurabans.core.platform.Platform;
import net.serlith.sakurabans.core.storage.repository.InstanceRepository;
import net.serlith.sakurabans.core.util.BuildConstants;
import net.serlith.sakurabans.core.util.Futures;
import org.jspecify.annotations.Nullable;

/**
 * The servers of the network (D35): each instance writes its name, categories, platform,
 * version and file values at start, on reload and on every heartbeat.
 */
public final class InstanceRegistry {

    public static final Duration HEARTBEAT = Duration.ofMinutes(1);
    public static final Duration STALE = Duration.ofDays(7);
    static final String ID_FILE = "instance-id";
    private static final Pattern ID = Pattern.compile("[0-9a-f-]{36}");

    private final Path dataDirectory;
    private final ConfigService config;
    private final Platform platform;
    private final InstanceRepository repository;
    private final SakuraExecutors executors;
    private final InstantSource clock;
    private final ComponentLogger logger;
    private final AtomicReference<@Nullable String> id = new AtomicReference<>();
    private final AtomicReference<@Nullable Instant> startedAt = new AtomicReference<>();
    private final AtomicReference<List<InstanceRepository.Instance>> known =
            new AtomicReference<>(List.of());
    private final AtomicReference<@Nullable ScheduledFuture<?>> heartbeat =
            new AtomicReference<>();

    public InstanceRegistry(final Path dataDirectory, final ConfigService config,
            final Platform platform, final InstanceRepository repository,
            final SakuraExecutors executors, final InstantSource clock,
            final ComponentLogger logger) {
        this.dataDirectory = dataDirectory;
        this.config = config;
        this.platform = platform;
        this.repository = repository;
        this.executors = executors;
        this.clock = clock;
        this.logger = logger;
    }

    /** Reads or creates this installation's id, writes the row and starts the heartbeat. */
    public CompletableFuture<Void> start() {
        this.id.set(this.installationId());
        this.startedAt.set(this.clock.instant());
        final ScheduledFuture<?> previous = this.heartbeat.getAndSet(this.executors.repeat(
                HEARTBEAT, HEARTBEAT, () -> Futures.logged(this.refresh(), this.logger,
                        "refresh this server in the instance registry").exceptionally(
                                failure -> null).join()));
        if (previous != null) {
            previous.cancel(false);
        }
        return this.refresh();
    }

    public void stop() {
        final ScheduledFuture<?> current = this.heartbeat.getAndSet(null);
        if (current != null) {
            current.cancel(false);
        }
    }

    /** Writes this server's row (on start, reload and heartbeat) and reads the others. */
    public CompletableFuture<Void> refresh() {
        final String self = this.id.get();
        final Instant started = this.startedAt.get();
        if (self == null || started == null) {
            return CompletableFuture.completedFuture(null);
        }
        final Instant now = this.clock.instant();
        final var server = this.config.settings().server();
        final InstanceRepository.Instance row = new InstanceRepository.Instance(self,
                server.name() == null ? "" : server.name(), server.categories(),
                this.platform.platformName(), BuildConstants.VERSION, started, now,
                EditorJson.GSON.toJson(EditorJson.json(this.fileValues())));
        return this.repository.write(row, now.minus(STALE))
                .thenCompose(ignored -> this.repository.all())
                .thenAccept(this.known::set);
    }

    /** This server's row as it would be written now. */
    public InstanceRepository.Instance self() {
        final var server = this.config.settings().server();
        final Instant now = this.clock.instant();
        return new InstanceRepository.Instance(this.id().orElse(""),
                server.name() == null ? "" : server.name(), server.categories(),
                this.platform.platformName(), BuildConstants.VERSION,
                Optional.ofNullable(this.startedAt.get()).orElse(now), now,
                EditorJson.GSON.toJson(EditorJson.json(this.fileValues())));
    }

    /** The servers as of the last heartbeat, the most recently seen first. */
    public List<InstanceRepository.Instance> instances() {
        return this.known.get();
    }

    public Optional<String> id() {
        return Optional.ofNullable(this.id.get());
    }

    /** What this server's files say for every key that isn't file-only (plain trees). */
    public Map<String, @Nullable Object> fileValues() {
        final Map<String, @Nullable Object> values = new LinkedHashMap<>();
        for (final SettingsSchema.Entry entry : SettingsSchema.ENTRIES) {
            if (entry.editable()) {
                values.put(entry.key(), this.config.fileValue(entry.key()).orElse(null));
            }
        }
        return values;
    }

    /** A server's file values from its row: plain trees by key. */
    @SuppressWarnings("unchecked")
    public static Map<String, @Nullable Object> fileValues(
            final InstanceRepository.Instance instance) {
        final Object tree = EditorJson.plain(JsonParser.parseString(
                instance.fileValues()));
        return tree instanceof Map<?, ?> map ? (Map<String, @Nullable Object>) map : Map.of();
    }

    private String installationId() {
        final Path file = this.dataDirectory.resolve(ID_FILE);
        try {
            if (Files.isRegularFile(file)) {
                final String stored = Files.readString(file, StandardCharsets.UTF_8).strip();
                if (ID.matcher(stored).matches()) {
                    return stored;
                }
            }
            final String created = UUID.randomUUID().toString();
            Files.writeString(file, created + "\n", StandardCharsets.UTF_8);
            return created;
        } catch (final IOException e) {
            this.logger.warn("Couldn't keep this server's registry id in {}; it changes with"
                    + " every start: {}", file, e.toString());
            return UUID.randomUUID().toString();
        }
    }
}
