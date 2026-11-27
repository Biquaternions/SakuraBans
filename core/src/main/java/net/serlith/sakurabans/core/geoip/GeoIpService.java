package net.serlith.sakurabans.core.geoip;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.Optional;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import net.serlith.sakurabans.core.concurrent.SakuraExecutors;
import net.serlith.sakurabans.core.configuration.ConfigService;
import net.serlith.sakurabans.core.configuration.settings.Settings;
import net.serlith.sakurabans.core.util.InetAddresses;
import org.jspecify.annotations.Nullable;

public final class GeoIpService implements CountryLookup {

    public static final String FOLDER = "geoip";
    public static final String FILE = GeoIpDownloader.EDITION + ".mmdb";
    static final Duration TICK = Duration.ofHours(1);
    static final Duration RETRY = Duration.ofHours(6);

    private record Loaded(CountryDatabase database, @Nullable FileTime modified) {
    }

    private final Path directory;
    private final ConfigService config;
    private final SakuraExecutors executors;
    private final CountryDatabase.Opener opener;
    private final GeoIpDownloader downloader;
    private final InstantSource clock;
    private final ComponentLogger logger;
    private final AtomicReference<State> state = new AtomicReference<>(State.DISABLED);
    private final AtomicReference<@Nullable Loaded> loaded = new AtomicReference<>();
    private final AtomicReference<@Nullable ScheduledFuture<?>> task = new AtomicReference<>();
    private final AtomicReference<@Nullable Instant> notBefore = new AtomicReference<>();
    private final AtomicReference<@Nullable Instant> lastCheck = new AtomicReference<>();
    private final AtomicReference<@Nullable FileTime> rejected = new AtomicReference<>();
    private final AtomicBoolean warnedMissing = new AtomicBoolean();
    private final AtomicBoolean ran = new AtomicBoolean();

    public GeoIpService(final Path dataDirectory, final ConfigService config,
            final SakuraExecutors executors, final CountryDatabase.Opener opener,
            final GeoIpDownloader downloader, final InstantSource clock,
            final ComponentLogger logger) {
        this.directory = dataDirectory.resolve(FOLDER);
        this.config = config;
        this.executors = executors;
        this.opener = opener;
        this.downloader = downloader;
        this.clock = clock;
        this.logger = logger;
    }

    public void start() {
        if (!this.enable()) {
            return;
        }
        final ScheduledFuture<?> previous = this.task.getAndSet(
                this.executors.repeat(Duration.ZERO, TICK, this::refresh));
        if (previous != null) {
            previous.cancel(false);
        }
    }

    boolean enable() {
        if (!this.config.settings().geoip().enabled()) {
            this.state.set(State.DISABLED);
            return false;
        }
        this.state.set(State.NO_DATABASE);
        return true;
    }

    public void stop() {
        final ScheduledFuture<?> current = this.task.getAndSet(null);
        if (current != null) {
            current.cancel(false);
        }
        this.state.set(State.DISABLED);
        this.downloader.stop();
        final Loaded old = this.loaded.getAndSet(null);
        if (old != null) {
            old.database().close();
        }
    }

    @Override
    public State state() {
        return this.state.get();
    }

    @Override
    public Optional<Country> country(final InetAddress address) {
        final InetAddress normalized = InetAddresses.normalize(address);
        if (this.state.get() != State.READY || !locatable(normalized)) {
            return Optional.empty();
        }
        // A swap may close the database between reading it and using it: then the new one.
        for (int attempt = 0; attempt < 2; attempt++) {
            final Loaded current = this.loaded.get();
            if (current == null) {
                return Optional.empty();
            }
            try {
                return current.database().country(normalized);
            } catch (final IOException | RuntimeException e) {
                if (this.loaded.get() == current) {
                    return Optional.empty();
                }
            } catch (final LinkageError e) {
                this.off(e);
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    void refresh() {
        if (this.state.get() == State.DISABLED) {
            return;
        }
        final Path file = this.directory.resolve(FILE);
        if (this.firstRun()) {
            this.deleteLeftovers();
        }
        this.loadIfChanged(file);
        if (this.state.get() == State.DISABLED) {
            return;
        }
        final Settings.GeoIp settings = this.config.settings().geoip();
        if (!settings.downloads()) {
            // A file that can't be read was reported when it was opened.
            if (this.loaded.get() == null && modified(file) == null
                    && this.warnedMissing.compareAndSet(false, true)) {
                this.logger.warn("GeoIP is on, but there is no database at {} and no MaxMind"
                        + " account (geoip.account-id, geoip.license-key) to download one: every"
                        + " country is unknown until one is placed there", file);
            }
            return;
        }
        if (this.due(file, settings)) {
            this.download(file, settings);
        }
    }

    private boolean firstRun() {
        return this.ran.compareAndSet(false, true);
    }

    private boolean due(final Path file, final Settings.GeoIp settings) {
        final Instant now = this.clock.instant();
        final Instant blocked = this.notBefore.get();
        if (blocked != null && now.isBefore(blocked)) {
            return false;
        }
        final FileTime modified = modified(file);
        if (this.loaded.get() == null || modified == null) {
            return true;
        }
        if (settings.refresh() == null) {
            return false;
        }
        Instant since = modified.toInstant();
        final Instant checked = this.lastCheck.get();
        if (checked != null && checked.isAfter(since)) {
            since = checked;
        }
        return !now.isBefore(since.plus(settings.refresh()));
    }

    private void download(final Path file, final Settings.GeoIp settings) {
        final String account = settings.accountId();
        final String key = settings.licenseKey();
        if (account == null || key == null) {
            return;
        }
        // Without a usable database, whatever MaxMind has is newer.
        final FileTime modified = this.loaded.get() == null ? null : modified(file);
        final GeoIpDownloader.Result result = this.downloader.download(account, key,
                modified == null ? null : modified.toInstant(), this.directory);
        switch (result) {
            case GeoIpDownloader.Result.NotNewer notNewer -> {
                this.lastCheck.set(this.clock.instant());
                this.notBefore.set(null);
            }
            case GeoIpDownloader.Result.Failed failed -> this.failed(failed.reason());
            case GeoIpDownloader.Result.Downloaded downloaded ->
                    this.install(downloaded.file(), file, downloaded.lastModified());
        }
    }

    private void install(final Path downloaded, final Path file,
            final @Nullable Instant published) {
        final CountryDatabase database;
        try {
            database = this.opener.open(downloaded);
        } catch (final IOException | RuntimeException e) {
            delete(downloaded);
            this.failed("the downloaded file isn't a database MaxMind's reader can open ("
                    + e.getClass().getSimpleName() + ")");
            return;
        } catch (final LinkageError e) {
            delete(downloaded);
            this.off(e);
            return;
        }
        final FileTime time = FileTime.from(published != null ? published : this.clock.instant());
        try {
            Files.setLastModifiedTime(downloaded, time);
            Files.move(downloaded, file, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (final IOException e) {
            database.close();
            delete(downloaded);
            this.failed("the database can't be moved into place (" + e.getClass().getSimpleName()
                    + ")");
            return;
        }
        this.swap(new Loaded(database, modified(file)));
        this.lastCheck.set(this.clock.instant());
        this.notBefore.set(null);
        this.rejected.set(null);
        this.logger.info("GeoIP: downloaded a new {} database{}", GeoIpDownloader.EDITION,
                published == null ? "" : " (published " + published + ")");
    }

    private void failed(final String reason) {
        this.notBefore.set(this.clock.instant().plus(RETRY));
        this.logger.warn("GeoIP: the {} database couldn't be updated: {}. {}; trying again in"
                        + " {} hours", GeoIpDownloader.EDITION, reason,
                this.loaded.get() == null ? "Every country is unknown until then"
                        : "The current database stays in use", RETRY.toHours());
    }

    private void loadIfChanged(final Path file) {
        final FileTime modified = modified(file);
        if (modified == null) {
            return;
        }
        final Loaded current = this.loaded.get();
        if (current != null && modified.equals(current.modified())
                || modified.equals(this.rejected.get())) {
            return;
        }
        final CountryDatabase database;
        try {
            database = this.opener.open(file);
        } catch (final IOException | RuntimeException e) {
            this.rejected.set(modified);
            this.logger.warn("GeoIP: {} isn't a database that can be read ({}){}", file,
                    e.getClass().getSimpleName(), current == null
                            ? "; every country is unknown until it is replaced"
                            : "; the database loaded before stays in use");
            return;
        } catch (final LinkageError e) {
            this.off(e);
            return;
        }
        this.swap(new Loaded(database, modified));
        this.logger.info("GeoIP: loaded {}", file);
    }

    private void swap(final Loaded next) {
        final Loaded old = this.loaded.getAndSet(next);
        if (this.state.get() == State.DISABLED) {
            this.loaded.set(null);
            next.database().close();
            return;
        }
        this.state.set(State.READY);
        if (old != null) {
            // Lookups already inside it finish; a later one retries with the new database.
            old.database().close();
        }
    }

    private void off(final LinkageError e) {
        if (this.state.getAndSet(State.DISABLED) == State.DISABLED) {
            return;
        }
        this.logger.warn("GeoIP is off: MaxMind's reader library can't be loaded ({}: {})",
                e.getClass().getName(), e.getMessage());
        final ScheduledFuture<?> current = this.task.getAndSet(null);
        if (current != null) {
            current.cancel(false);
        }
        final Loaded old = this.loaded.getAndSet(null);
        if (old != null) {
            old.database().close();
        }
    }

    private void deleteLeftovers() {
        if (!Files.isDirectory(this.directory)) {
            return;
        }
        try (DirectoryStream<Path> parts = Files.newDirectoryStream(this.directory, "*.part")) {
            for (final Path part : parts) {
                delete(part);
            }
        } catch (final IOException e) {
            // Left for the next start.
        }
    }

    private static @Nullable FileTime modified(final Path file) {
        try {
            return Files.isRegularFile(file) ? Files.getLastModifiedTime(file) : null;
        } catch (final IOException e) {
            // Gone or unreadable: as if there were none.
            return null;
        }
    }

    private static void delete(final Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (final IOException e) {
            // Deleted at the next start.
        }
    }

    static boolean locatable(final InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress()
                || address.isLinkLocalAddress() || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return false;
        }
        final byte[] bytes = address.getAddress();
        if (address instanceof Inet4Address) {
            final int first = Byte.toUnsignedInt(bytes[0]);
            final int second = Byte.toUnsignedInt(bytes[1]);
            return first != 0 && first < 240 && !(first == 100 && second >= 64 && second < 128)
                    && !(first == 192 && second == 0 && Byte.toUnsignedInt(bytes[2]) == 0);
        }
        if (address instanceof Inet6Address) {
            final int first = Byte.toUnsignedInt(bytes[0]);
            // Global unicast is 2000::/3; the rest is reserved, unique local or special.
            return (first & 0xe0) == 0x20;
        }
        return false;
    }
}
