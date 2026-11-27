package net.serlith.sakurabans.core.geoip;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.InstantSource;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class GeoIpServiceTest {

    // Nothing listens here: a request would fail and log a warning.
    private static final URI NOWHERE = URI.create("http://127.0.0.1:9/geoip/databases/");

    @TempDir
    Path dir;

    private GeoIpFixture fixture;

    private GeoIpFixture fixture(final String yaml) {
        this.fixture = new GeoIpFixture(this.dir, yaml, InstantSource.system(), NOWHERE);
        return this.fixture;
    }

    private GeoIpFixture fixture(final String yaml, final CountryDatabase.Opener opener) {
        this.fixture = new GeoIpFixture(this.dir, yaml, InstantSource.system(), opener,
                new GeoIpDownloader(NOWHERE, Runnable::run));
        return this.fixture;
    }

    @AfterEach
    void close() {
        if (this.fixture != null) {
            this.fixture.close();
        }
    }

    @Test
    void offByDefaultAndEveryLookupIsEmpty() {
        GeoIpTestFiles.place(this.dir);
        final GeoIpFixture geo = this.fixture("");

        geo.service.start();
        geo.service.refresh();

        Assertions.assertThat(geo.config.settings().geoip().enabled()).isFalse();
        Assertions.assertThat(geo.service.state()).isEqualTo(CountryLookup.State.DISABLED);
        Assertions.assertThat(geo.service.country(GeoIpTestFiles.address("81.2.69.142")))
                .isEmpty();
        Assertions.assertThat(geo.allLines()).noneMatch(line -> line.contains("GeoIP"));
    }

    @Test
    void theNoOpLookupIsAlwaysOff() {
        Assertions.assertThat(CountryLookup.disabled().state())
                .isEqualTo(CountryLookup.State.DISABLED);
        Assertions.assertThat(CountryLookup.disabled().country(
                GeoIpTestFiles.address("81.2.69.142"))).isEmpty();
    }

    @Test
    void filePlacedByHandIsUsedWithoutAccount() {
        GeoIpTestFiles.place(this.dir);
        final GeoIpFixture geo = this.fixture(GeoIpFixture.ON);

        Assertions.assertThat(geo.service.enable()).isTrue();
        geo.service.refresh();

        Assertions.assertThat(geo.service.state()).isEqualTo(CountryLookup.State.READY);
        Assertions.assertThat(geo.service.country(GeoIpTestFiles.address("81.2.69.142")))
                .contains(new Country("GB", "United Kingdom"));
        Assertions.assertThat(geo.service.country(GeoIpTestFiles.address("2001:218::1")))
                .contains(new Country("JP", "Japan"));
        // An IPv4 address written as IPv6.
        Assertions.assertThat(geo.service.country(GeoIpTestFiles.address("::ffff:89.160.20.112")))
                .map(Country::code).contains("SE");
        Assertions.assertThat(geo.lines("warn")).isEmpty();
        Assertions.assertThat(geo.lines("info")).filteredOn(line -> line.startsWith("GeoIP"))
                .containsExactly("GeoIP: loaded " + geo.file());
    }

    @ParameterizedTest
    @ValueSource(strings = {"127.0.0.1", "10.1.2.3", "172.16.0.1", "192.168.1.20", "169.254.1.1",
            "100.64.0.1", "0.0.0.0", "240.0.0.1", "255.255.255.255", "224.0.0.1", "192.0.0.8",
            "::1", "::", "fe80::1", "fc00::1", "fd12:3456::1", "ff02::1"})
    void privateLoopbackAndReservedAddressesHaveNoCountry(final String literal) {
        Assertions.assertThat(GeoIpService.locatable(GeoIpTestFiles.address(literal))).isFalse();
    }

    @Test
    void publicAddressesCanBeLocated() {
        Assertions.assertThat(GeoIpService.locatable(GeoIpTestFiles.address("81.2.69.142")))
                .isTrue();
        Assertions.assertThat(GeoIpService.locatable(GeoIpTestFiles.address("2a02:d040::1")))
                .isTrue();
    }

    @Test
    void privateAddressesAreNotLookedUpEvenWithDatabase() {
        GeoIpTestFiles.place(this.dir);
        final GeoIpFixture geo = this.fixture(GeoIpFixture.ON);
        geo.service.enable();
        geo.service.refresh();

        Assertions.assertThat(geo.service.country(GeoIpTestFiles.address("127.0.0.1"))).isEmpty();
        Assertions.assertThat(geo.service.country(GeoIpTestFiles.address("192.168.0.1")))
                .isEmpty();
        Assertions.assertThat(geo.service.country(GeoIpTestFiles.address("::1"))).isEmpty();
    }

    @Test
    void noFileAndNoAccountWarnsOnceAndEveryCountryIsUnknown() {
        final GeoIpFixture geo = this.fixture(GeoIpFixture.ON);
        geo.service.enable();

        geo.service.refresh();
        geo.service.refresh();

        Assertions.assertThat(geo.service.state()).isEqualTo(CountryLookup.State.NO_DATABASE);
        Assertions.assertThat(geo.service.country(GeoIpTestFiles.address("81.2.69.142")))
                .isEmpty();
        Assertions.assertThat(geo.lines("warn")).singleElement().asString()
                .contains("no database at " + geo.file(), "geoip.account-id");
    }

    @Test
    void filePlacedLaterIsPickedUpByTheNextRun() {
        final GeoIpFixture geo = this.fixture(GeoIpFixture.ON);
        geo.service.enable();
        geo.service.refresh();

        GeoIpTestFiles.place(this.dir);
        geo.service.refresh();

        Assertions.assertThat(geo.service.state()).isEqualTo(CountryLookup.State.READY);
        Assertions.assertThat(geo.service.country(GeoIpTestFiles.address("89.160.20.112")))
                .map(Country::code).contains("SE");
    }

    @Test
    void corruptFileWarnsOnceAndLeavesEveryCountryUnknown() throws IOException {
        Files.createDirectories(this.dir.resolve(GeoIpService.FOLDER));
        Files.writeString(this.dir.resolve(GeoIpService.FOLDER).resolve(GeoIpService.FILE),
                "this is not a database");
        final GeoIpFixture geo = this.fixture(GeoIpFixture.ON);
        geo.service.enable();

        geo.service.refresh();
        geo.service.refresh();

        Assertions.assertThat(geo.service.state()).isEqualTo(CountryLookup.State.NO_DATABASE);
        Assertions.assertThat(geo.service.country(GeoIpTestFiles.address("81.2.69.142")))
                .isEmpty();
        Assertions.assertThat(geo.lines("warn")).singleElement().asString()
                .contains("isn't a database that can be read", "every country is unknown");
    }

    @Test
    void linkageErrorTurnsGeoIpOffWithOneWarning() {
        GeoIpTestFiles.place(this.dir);
        final GeoIpFixture geo = this.fixture(GeoIpFixture.ON, file -> {
            throw new NoClassDefFoundError("com/maxmind/db/Reader");
        });
        geo.service.enable();

        geo.service.refresh();
        geo.service.refresh();

        Assertions.assertThat(geo.service.state()).isEqualTo(CountryLookup.State.DISABLED);
        Assertions.assertThat(geo.service.country(GeoIpTestFiles.address("81.2.69.142")))
                .isEmpty();
        Assertions.assertThat(geo.lines("warn")).singleElement().asString()
                .contains("GeoIP is off", "NoClassDefFoundError");
    }

    @Test
    void stopClosesTheDatabase() {
        GeoIpTestFiles.place(this.dir);
        final GeoIpFixture geo = this.fixture(GeoIpFixture.ON);
        geo.service.enable();
        geo.service.refresh();

        geo.service.stop();

        Assertions.assertThat(geo.service.state()).isEqualTo(CountryLookup.State.DISABLED);
        Assertions.assertThat(geo.service.country(GeoIpTestFiles.address("81.2.69.142")))
                .isEmpty();
    }

    @Test
    void startRunsTheFirstLoadOnTheWorker() throws InterruptedException {
        GeoIpTestFiles.place(this.dir);
        final GeoIpFixture geo = this.fixture(GeoIpFixture.ON);

        geo.service.start();

        for (int i = 0; i < 200 && geo.service.state() != CountryLookup.State.READY; i++) {
            Thread.sleep(10);
        }
        Assertions.assertThat(geo.service.state()).isEqualTo(CountryLookup.State.READY);
    }
}
