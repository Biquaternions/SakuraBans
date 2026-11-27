package net.serlith.sakurabans.core.geoip;

import java.net.URI;
import java.nio.file.Path;
import java.time.InstantSource;
import java.util.List;
import java.util.stream.Stream;
import net.serlith.sakurabans.core.RecordingLogger;
import net.serlith.sakurabans.core.TestConfig;
import net.serlith.sakurabans.core.concurrent.SakuraExecutors;
import net.serlith.sakurabans.core.configuration.ConfigService;

/** A GeoIP service on a test configuration, its log and its executors. */
final class GeoIpFixture implements AutoCloseable {

    static final String ON = "geoip:\n  enabled: true\n";
    static final String WITH_ACCOUNT = "geoip:\n  enabled: true\n  account-id: '"
            + FakeMaxMind.ACCOUNT + "'\n  license-key: " + FakeMaxMind.KEY + "\n";

    final RecordingLogger log = new RecordingLogger();
    final SakuraExecutors executors = new SakuraExecutors(failure -> { });
    final Path dir;
    final ConfigService config;
    final GeoIpService service;

    GeoIpFixture(final Path dir, final String yaml, final InstantSource clock,
            final CountryDatabase.Opener opener, final GeoIpDownloader downloader) {
        this.dir = dir;
        this.config = TestConfig.load(dir, yaml, this.log);
        this.service = new GeoIpService(dir, this.config, this.executors, opener, downloader,
                clock, this.log.logger());
    }

    GeoIpFixture(final Path dir, final String yaml, final InstantSource clock, final URI base) {
        this(dir, yaml, clock, file -> MaxMindCountryDatabase.open(file),
                new GeoIpDownloader(base, Runnable::run));
    }

    Path file() {
        return this.dir.resolve(GeoIpService.FOLDER).resolve(GeoIpService.FILE);
    }

    /** The lines of a level, without the configuration's notes about missing keys. */
    List<String> lines(final String level) {
        return this.log.entries(level).stream().map(RecordingLogger.Entry::format)
                .filter(line -> !line.startsWith("Configuration: ")).toList();
    }

    /** Every line logged, at any level. */
    List<String> allLines() {
        return Stream.of("trace", "debug", "info", "warn", "error")
                .flatMap(level -> this.lines(level).stream()).toList();
    }

    @Override
    public void close() {
        this.service.stop();
        this.executors.close();
    }
}
