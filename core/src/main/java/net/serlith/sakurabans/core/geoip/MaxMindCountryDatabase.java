package net.serlith.sakurabans.core.geoip;

import com.maxmind.db.MaxMindDbParameter;
import com.maxmind.db.Reader;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

public final class MaxMindCountryDatabase implements CountryDatabase {

    private final Reader reader;

    private MaxMindCountryDatabase(final Reader reader) {
        this.reader = reader;
    }

    public static CountryDatabase open(final Path file) throws IOException {
        return new MaxMindCountryDatabase(new Reader(file.toFile(), Reader.FileMode.MEMORY));
    }

    @Override
    public Optional<Country> country(final InetAddress address) throws IOException {
        final Entry entry = this.reader.get(address, Entry.class);
        if (entry == null) {
            return Optional.empty();
        }
        final Place country = entry.country();
        final Place registered = entry.registeredCountry();
        return Optional.ofNullable(Country.fromRecord(
                country == null ? null : country.isoCode(), country == null ? null
                        : country.englishName(),
                registered == null ? null : registered.isoCode(), registered == null ? null
                        : registered.englishName()));
    }

    @Override
    public void close() {
        try {
            this.reader.close();
        } catch (final IOException e) {
            // A reader in memory holds no file; nothing is left to release.
        }
    }

    public record Entry(@MaxMindDbParameter(name = "country") @Nullable Place country,
            @MaxMindDbParameter(name = "registered_country") @Nullable Place registeredCountry) {
    }

    public record Place(@MaxMindDbParameter(name = "iso_code") @Nullable String isoCode,
            @MaxMindDbParameter(name = "names") @Nullable Map<String, String> names) {

        @Nullable String englishName() {
            return this.names == null ? null : this.names.get("en");
        }
    }
}
