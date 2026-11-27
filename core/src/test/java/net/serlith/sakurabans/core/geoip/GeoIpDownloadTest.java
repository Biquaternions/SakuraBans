package net.serlith.sakurabans.core.geoip;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** The download from a local stand-in for MaxMind's service. */
class GeoIpDownloadTest {

    private static final Instant PUBLISHED = Instant.parse("2026-11-24T10:00:00Z");
    private static final Instant NOW = Instant.parse("2026-11-27T12:00:00Z");

    @TempDir
    Path dir;

    private final FakeMaxMind maxmind = new FakeMaxMind();
    private final List<GeoIpFixture> fixtures = new ArrayList<>();

    private GeoIpFixture fixture(final InstantSource clock) {
        return this.fixture(new GeoIpDownloader(this.maxmind.base(), Runnable::run), clock);
    }

    private GeoIpFixture fixture(final GeoIpDownloader downloader, final InstantSource clock) {
        final GeoIpFixture geo = new GeoIpFixture(this.dir, GeoIpFixture.WITH_ACCOUNT, clock,
                file -> MaxMindCountryDatabase.open(file), downloader);
        this.fixtures.add(geo);
        geo.service.enable();
        return geo;
    }

    @AfterEach
    void close() {
        // The account is in no line, whatever the test did.
        for (final GeoIpFixture geo : this.fixtures) {
            final String basic = Base64.getEncoder().encodeToString((FakeMaxMind.ACCOUNT + ":"
                    + FakeMaxMind.KEY).getBytes(StandardCharsets.UTF_8));
            Assertions.assertThat(geo.allLines()).noneMatch(line -> line.contains(
                    FakeMaxMind.ACCOUNT) || line.contains(FakeMaxMind.KEY)
                    || line.contains(basic) || line.contains(FakeMaxMind.SIGNATURE));
            Assertions.assertThat(geo.config.settings().toString())
                    .doesNotContain(FakeMaxMind.ACCOUNT, FakeMaxMind.KEY);
            geo.close();
        }
        this.maxmind.close();
    }

    /** The test database as the current local copy, published at {@code published}. */
    private Path placeOld(final Instant published) throws IOException {
        final Path file = GeoIpTestFiles.place(this.dir);
        Files.setLastModifiedTime(file, FileTime.from(published));
        return file;
    }

    private List<Path> leftovers() throws IOException {
        try (Stream<Path> files = Files.list(this.dir.resolve(GeoIpService.FOLDER))) {
            return files.filter(file -> file.getFileName().toString().endsWith(".part")).toList();
        }
    }

    @Test
    void downloadsThroughRedirectAndOnlyTheFirstRequestCarriesTheAccount() throws IOException {
        final GeoIpFixture geo = this.fixture(InstantSource.fixed(NOW));

        geo.service.refresh();

        Assertions.assertThat(geo.service.state()).isEqualTo(CountryLookup.State.READY);
        Assertions.assertThat(geo.service.country(GeoIpTestFiles.address("81.2.69.142")))
                .contains(new Country("GB", "United Kingdom"));
        Assertions.assertThat(Files.readAllBytes(geo.file()))
                .isEqualTo(GeoIpTestFiles.database());
        Assertions.assertThat(Files.getLastModifiedTime(geo.file()).toInstant())
                .isEqualTo(PUBLISHED);
        Assertions.assertThat(this.maxmind.requests).extracting(FakeMaxMind.Request::method,
                        FakeMaxMind.Request::path, FakeMaxMind.Request::query)
                .containsExactly(
                        Assertions.tuple("HEAD", "/geoip/databases/GeoLite2-Country/download",
                                "suffix=tar.gz"),
                        Assertions.tuple("GET", "/geoip/databases/GeoLite2-Country/download",
                                "suffix=tar.gz.sha256"),
                        Assertions.tuple("GET", "/geoip/databases/GeoLite2-Country/download",
                                "suffix=tar.gz"),
                        Assertions.tuple("GET", "/storage/GeoLite2-Country.tar.gz",
                                FakeMaxMind.SIGNATURE));
        Assertions.assertThat(this.maxmind.requests.subList(0, 3))
                .extracting(FakeMaxMind.Request::authorization)
                .containsOnly(this.maxmind.expectedAuthorization());
        Assertions.assertThat(this.maxmind.requests.get(3).authorization()).isNull();
        Assertions.assertThat(this.maxmind.requests).extracting(FakeMaxMind.Request::userAgent)
                .containsOnly("SakuraBans");
        Assertions.assertThat(geo.lines("warn")).isEmpty();
        Assertions.assertThat(geo.lines("info")).filteredOn(line -> line.startsWith("GeoIP"))
                .containsExactly("GeoIP: downloaded a new GeoLite2-Country database"
                        + " (published 2026-11-24T10:00:00Z)");
        Assertions.assertThat(this.leftovers()).isEmpty();
    }

    @Test
    void headSayingNothingIsNewerDownloadsNothing() throws IOException {
        this.placeOld(PUBLISHED);
        // Due: more than the 7 days of geoip.refresh after the file.
        final GeoIpFixture geo = this.fixture(InstantSource.fixed(PUBLISHED.plus(
                Duration.ofDays(8))));

        geo.service.refresh();
        geo.service.refresh();

        Assertions.assertThat(this.maxmind.requests).extracting(FakeMaxMind.Request::method)
                .containsExactly("HEAD");
        Assertions.assertThat(geo.service.state()).isEqualTo(CountryLookup.State.READY);
        Assertions.assertThat(geo.lines("warn")).isEmpty();
    }

    @Test
    void nothingIsAskedBeforeTheRefreshIsDue() throws IOException {
        this.placeOld(PUBLISHED);
        final GeoIpFixture geo = this.fixture(InstantSource.fixed(PUBLISHED.plus(
                Duration.ofDays(6))));

        geo.service.refresh();

        Assertions.assertThat(this.maxmind.requests).isEmpty();
        Assertions.assertThat(geo.service.state()).isEqualTo(CountryLookup.State.READY);
    }

    @Test
    void newerDatabaseReplacesTheOldFile() throws IOException {
        final Path file = this.placeOld(PUBLISHED.minus(Duration.ofDays(10)));
        final GeoIpFixture geo = this.fixture(InstantSource.fixed(NOW));

        geo.service.refresh();

        Assertions.assertThat(this.maxmind.gets()).hasSize(3);
        Assertions.assertThat(Files.getLastModifiedTime(file).toInstant()).isEqualTo(PUBLISHED);
        Assertions.assertThat(geo.lines("info")).anyMatch(line -> line.startsWith(
                "GeoIP: downloaded"));
    }

    @Test
    void badChecksumKeepsTheOldFile() throws IOException {
        final Path file = this.placeOld(PUBLISHED.minus(Duration.ofDays(10)));
        this.maxmind.checksum = "0".repeat(64);
        // Different bytes, so a replaced file would show.
        this.maxmind.archive = FakeMaxMind.archive(new byte[] {1, 2, 3});
        final GeoIpFixture geo = this.fixture(InstantSource.fixed(NOW));

        geo.service.refresh();

        Assertions.assertThat(Files.readAllBytes(file)).isEqualTo(GeoIpTestFiles.database());
        Assertions.assertThat(geo.service.state()).isEqualTo(CountryLookup.State.READY);
        Assertions.assertThat(geo.lines("warn")).singleElement().asString()
                .contains("doesn't match MaxMind's checksum", "current database stays in use");
        Assertions.assertThat(this.leftovers()).isEmpty();
    }

    @Test
    void checksumThatIsNoSha256RefusesTheDownload() throws IOException {
        this.placeOld(PUBLISHED.minus(Duration.ofDays(10)));
        this.maxmind.checksum = "not-a-checksum";
        final GeoIpFixture geo = this.fixture(InstantSource.fixed(NOW));

        geo.service.refresh();

        Assertions.assertThat(this.maxmind.gets()).hasSize(1);
        Assertions.assertThat(geo.lines("warn")).singleElement().asString()
                .contains("isn't a SHA-256");
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 429})
    void refusedRequestsWarnAndKeepTheOldFile(final int status) throws IOException {
        final Path file = this.placeOld(PUBLISHED.minus(Duration.ofDays(10)));
        this.maxmind.headStatus = status;
        final GeoIpFixture geo = this.fixture(InstantSource.fixed(NOW));

        geo.service.refresh();

        Assertions.assertThat(this.maxmind.gets()).isEmpty();
        Assertions.assertThat(Files.readAllBytes(file)).isEqualTo(GeoIpTestFiles.database());
        Assertions.assertThat(geo.service.country(GeoIpTestFiles.address("81.2.69.142")))
                .map(Country::code).contains("GB");
        Assertions.assertThat(geo.lines("warn")).singleElement().asString()
                .contains("(" + status + ")", "trying again in 6 hours");
    }

    @Test
    void failedDownloadIsTriedAgainOnlyAfterTheRetryDelay() throws IOException {
        this.placeOld(PUBLISHED.minus(Duration.ofDays(10)));
        this.maxmind.headStatus = 429;
        final MutableClock clock = new MutableClock(NOW);
        final GeoIpFixture geo = this.fixture(clock);

        geo.service.refresh();
        clock.now = NOW.plus(Duration.ofHours(5));
        geo.service.refresh();
        clock.now = NOW.plus(Duration.ofHours(6));
        this.maxmind.headStatus = 200;
        geo.service.refresh();

        Assertions.assertThat(this.maxmind.requests).extracting(FakeMaxMind.Request::method)
                .containsExactly("HEAD", "HEAD", "GET", "GET", "GET");
    }

    @Test
    void wrongAccountIsRefusedByTheServer() throws IOException {
        final GeoIpFixture geo = new GeoIpFixture(this.dir, "geoip:\n  enabled: true\n"
                + "  account-id: '999'\n  license-key: wrong\n", InstantSource.fixed(NOW),
                file -> MaxMindCountryDatabase.open(file),
                new GeoIpDownloader(this.maxmind.base(), Runnable::run));
        this.fixtures.add(geo);
        geo.service.enable();

        geo.service.refresh();

        Assertions.assertThat(geo.service.state()).isEqualTo(CountryLookup.State.NO_DATABASE);
        Assertions.assertThat(geo.lines("warn")).singleElement().asString()
                .contains("refused the account ID or licence key (401)",
                        "Every country is unknown")
                .doesNotContain("999", "wrong");
    }

    @Test
    void archiveWithoutDatabaseIsRefused() throws IOException {
        this.maxmind.archive = FakeMaxMind.archive(List.of(new FakeMaxMind.TarEntry(
                "GeoLite2-Country_20261124/LICENSE.txt",
                "no database here".getBytes(StandardCharsets.US_ASCII))));
        final GeoIpFixture geo = this.fixture(InstantSource.fixed(NOW));

        geo.service.refresh();

        Assertions.assertThat(geo.service.state()).isEqualTo(CountryLookup.State.NO_DATABASE);
        Assertions.assertThat(Files.exists(geo.file())).isFalse();
        Assertions.assertThat(geo.lines("warn")).singleElement().asString()
                .contains("holds no .mmdb file");
        Assertions.assertThat(this.leftovers()).isEmpty();
    }

    @Test
    void archiveOverTheCapIsRefused() throws IOException {
        final GeoIpFixture geo = this.fixture(new GeoIpDownloader(this.maxmind.base(), 1024,
                GeoIpDownloader.MAX_DATABASE, Runnable::run), InstantSource.fixed(NOW));

        geo.service.refresh();

        Assertions.assertThat(Files.exists(geo.file())).isFalse();
        Assertions.assertThat(geo.lines("warn")).singleElement().asString()
                .contains("the archive is larger than 1024 bytes");
        Assertions.assertThat(this.leftovers()).isEmpty();
    }

    @Test
    void databaseOverTheCapIsRefused() throws IOException {
        final GeoIpFixture geo = this.fixture(new GeoIpDownloader(this.maxmind.base(),
                GeoIpDownloader.MAX_ARCHIVE, 1024, Runnable::run), InstantSource.fixed(NOW));

        geo.service.refresh();

        Assertions.assertThat(Files.exists(geo.file())).isFalse();
        Assertions.assertThat(geo.lines("warn")).singleElement().asString()
                .contains("larger than 1024 bytes");
        Assertions.assertThat(this.leftovers()).isEmpty();
    }

    @Test
    void downloadedFileThatIsNoDatabaseIsRefusedAndTheOldOneKept() throws IOException {
        final Path file = this.placeOld(PUBLISHED.minus(Duration.ofDays(10)));
        this.maxmind.archive = FakeMaxMind.archive("garbage".getBytes(StandardCharsets.US_ASCII));
        final GeoIpFixture geo = this.fixture(InstantSource.fixed(NOW));

        geo.service.refresh();

        Assertions.assertThat(Files.readAllBytes(file)).isEqualTo(GeoIpTestFiles.database());
        Assertions.assertThat(geo.service.state()).isEqualTo(CountryLookup.State.READY);
        Assertions.assertThat(geo.lines("warn")).singleElement().asString()
                .contains("isn't a database MaxMind's reader can open");
        Assertions.assertThat(this.leftovers()).isEmpty();
    }

    @Test
    void refreshSwapsTheDatabaseWhileLookupsGoOn() throws Exception {
        this.placeOld(PUBLISHED.minus(Duration.ofDays(10)));
        final MutableClock clock = new MutableClock(NOW);
        final GeoIpFixture geo = this.fixture(clock);
        geo.service.refresh();
        geo.service.refresh();
        Assertions.assertThat(geo.service.state()).isEqualTo(CountryLookup.State.READY);

        final AtomicBoolean running = new AtomicBoolean(true);
        final List<String> wrong = new CopyOnWriteArrayList<>();
        final List<Thread> readers = new ArrayList<>();
        for (int reader = 0; reader < 4; reader++) {
            readers.add(Thread.ofPlatform().start(() -> {
                while (running.get()) {
                    final String code = geo.service.country(GeoIpTestFiles.address(
                            "89.160.20.112")).map(Country::code).orElse("none");
                    if (!code.equals("SE")) {
                        wrong.add(code);
                    }
                }
            }));
        }
        for (int round = 1; round <= 5; round++) {
            this.maxmind.lastModified = PUBLISHED.plus(Duration.ofDays(round));
            clock.now = NOW.plus(Duration.ofDays(8L * round));
            geo.service.refresh();
        }
        running.set(false);
        for (final Thread reader : readers) {
            reader.join();
        }

        Assertions.assertThat(wrong).isEmpty();
        Assertions.assertThat(geo.lines("info")).filteredOn(line -> line.startsWith(
                "GeoIP: downloaded")).hasSize(6);
        Assertions.assertThat(Files.getLastModifiedTime(geo.file()).toInstant())
                .isEqualTo(PUBLISHED.plus(Duration.ofDays(5)));
    }

    @Test
    void checksumTakesTheFirstTokenOfSixtyFourHexDigits() {
        final String sum = "AB".repeat(32);
        Assertions.assertThat(GeoIpDownloader.checksum((sum + "  file.tar.gz\n")
                .getBytes(StandardCharsets.US_ASCII))).isEqualTo(sum.toLowerCase());
        Assertions.assertThat(GeoIpDownloader.checksum("".getBytes(StandardCharsets.US_ASCII)))
                .isNull();
        Assertions.assertThat(GeoIpDownloader.checksum("abc def".getBytes(
                StandardCharsets.US_ASCII))).isNull();
    }

    private static final class MutableClock implements InstantSource {

        private volatile Instant now;

        private MutableClock(final Instant now) {
            this.now = now;
        }

        @Override
        public Instant instant() {
            return this.now;
        }
    }
}
