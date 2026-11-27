package net.serlith.sakurabans.core.bootstrap;

import java.net.InetAddress;
import java.nio.file.Path;
import java.time.InstantSource;
import java.util.function.Consumer;
import net.serlith.sakurabans.core.RecordingLogger;
import net.serlith.sakurabans.core.configuration.ConfigException;
import net.serlith.sakurabans.core.geoip.CountryLookup;

/**
 * Builds the core as a platform does, with the shipped configuration (GeoIP off), and starts
 * GeoIP and looks an address up, as the start and the login check would.
 */
public final class GeoIpOffProbe implements Consumer<Path> {

    @Override
    public void accept(final Path dataDirectory) {
        final CoreComponents components = new CoreComponents(new CoreEnvironment(dataDirectory,
                new RecordingLogger().logger(), false), InstantSource.system());
        try {
            components.config().load();
        } catch (final ConfigException e) {
            throw new IllegalStateException(e);
        }
        components.geoip().start();
        if (components.geoip().state() != CountryLookup.State.DISABLED
                || components.geoip().country(InetAddress.ofLiteral("81.2.69.142")).isPresent()) {
            throw new IllegalStateException("GeoIP isn't off");
        }
        components.geoip().stop();
        components.executors().close();
    }
}
