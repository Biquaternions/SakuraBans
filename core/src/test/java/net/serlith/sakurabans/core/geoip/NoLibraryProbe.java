package net.serlith.sakurabans.core.geoip;

import java.net.InetAddress;
import java.nio.file.Path;
import java.time.InstantSource;
import java.util.function.Consumer;

/** GeoIP on, with a database in place, where MaxMind's reader can't be loaded. */
public final class NoLibraryProbe implements Consumer<Path> {

    @Override
    public void accept(final Path dataDirectory) {
        try (GeoIpFixture geo = new GeoIpFixture(dataDirectory, GeoIpFixture.ON,
                InstantSource.system(), GeoIpDownloader.MAXMIND)) {
            geo.service.enable();
            geo.service.refresh();
            geo.service.refresh();
            final boolean off = geo.service.state() == CountryLookup.State.DISABLED
                    && geo.service.country(InetAddress.ofLiteral("81.2.69.142")).isEmpty();
            final var warnings = geo.lines("warn");
            if (!off || warnings.size() != 1 || !warnings.getFirst().contains("GeoIP is off")) {
                throw new IllegalStateException("not off with one warning: " + warnings);
            }
        }
    }
}
