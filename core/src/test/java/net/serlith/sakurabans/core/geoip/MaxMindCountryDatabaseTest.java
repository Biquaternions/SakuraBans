package net.serlith.sakurabans.core.geoip;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MaxMindCountryDatabaseTest {

    @TempDir
    Path dir;

    private CountryDatabase open() throws IOException {
        return MaxMindCountryDatabase.open(GeoIpTestFiles.place(this.dir));
    }

    @Test
    void knownAddressesHaveTheirCountryAndEnglishName() throws IOException {
        final CountryDatabase database = this.open();

        Assertions.assertThat(database.country(GeoIpTestFiles.address("2.125.160.216")))
                .contains(new Country("GB", "United Kingdom"));
        Assertions.assertThat(database.country(GeoIpTestFiles.address("81.2.69.142")))
                .contains(new Country("GB", "United Kingdom"));
        Assertions.assertThat(database.country(GeoIpTestFiles.address("89.160.20.112")))
                .contains(new Country("SE", "Sweden"));
        Assertions.assertThat(database.country(GeoIpTestFiles.address("2001:218::1")))
                .contains(new Country("JP", "Japan"));
        Assertions.assertThat(database.country(GeoIpTestFiles.address("2a02:d040::1")))
                .contains(new Country("SE", "Sweden"));
        database.close();
    }

    @Test
    void addressesTheDatabaseDoesntPlaceHaveNoCountry() throws IOException {
        final CountryDatabase database = this.open();

        Assertions.assertThat(database.country(GeoIpTestFiles.address("8.8.8.8"))).isEmpty();
        // Listed with a continent only.
        Assertions.assertThat(database.country(GeoIpTestFiles.address("2a02:d500::1")))
                .isEmpty();
        database.close();
    }

    @Test
    void theFileCanBeReplacedWhileTheDatabaseIsOpen() throws IOException {
        final Path file = GeoIpTestFiles.place(this.dir);
        final CountryDatabase database = MaxMindCountryDatabase.open(file);

        Files.delete(file);

        Assertions.assertThat(database.country(GeoIpTestFiles.address("81.2.69.142")))
                .map(Country::code).contains("GB");
        database.close();
    }

    @Test
    void closedDatabaseThrows() throws IOException {
        final CountryDatabase database = this.open();
        database.close();

        Assertions.assertThatThrownBy(() ->
                        database.country(GeoIpTestFiles.address("81.2.69.142")))
                .isInstanceOf(IOException.class);
    }

    @Test
    void fileThatIsNoDatabaseDoesntOpen() throws IOException {
        final Path file = this.dir.resolve("broken.mmdb");
        Files.writeString(file, "not a database");

        Assertions.assertThatThrownBy(() -> MaxMindCountryDatabase.open(file))
                .isInstanceOf(IOException.class);
    }
}
