package net.serlith.sakurabans.core.geoip;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.InstantSource;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import net.serlith.sakurabans.core.concurrent.SakuraExecutors;
import net.serlith.sakurabans.core.configuration.ConfigService;

/** MaxMind's test database (test resources, geoip/README) and addresses it lists. */
public final class GeoIpTestFiles {

    static final String RESOURCE = "/geoip/GeoLite2-Country-Test.mmdb";

    private GeoIpTestFiles() {
    }

    static byte[] database() {
        try (InputStream input = GeoIpTestFiles.class.getResourceAsStream(RESOURCE)) {
            if (input == null) {
                throw new IllegalStateException(RESOURCE + " is missing");
            }
            return input.readAllBytes();
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Copies the test database to {@code <data>/geoip/GeoLite2-Country.mmdb}. */
    public static Path place(final Path dataDirectory) {
        try {
            final Path file = dataDirectory.resolve(GeoIpService.FOLDER).resolve(GeoIpService.FILE);
            Files.createDirectories(file.getParent());
            try (InputStream input = GeoIpTestFiles.class.getResourceAsStream(RESOURCE)) {
                Files.copy(input, file, StandardCopyOption.REPLACE_EXISTING);
            }
            return file;
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static InetAddress address(final String literal) {
        return InetAddress.ofLiteral(literal);
    }

    /**
     * For tests of what uses GeoIP (S72b): a service on {@code config} (which must have
     * {@code geoip.enabled}) with the test database placed by hand and loaded, so it is
     * {@code READY} on return. Without an account it never makes a request; stop it after.
     */
    public static GeoIpService ready(final Path dataDirectory, final ConfigService config,
            final SakuraExecutors executors, final InstantSource clock,
            final ComponentLogger logger) {
        place(dataDirectory);
        final GeoIpService service = new GeoIpService(dataDirectory, config, executors,
                file -> MaxMindCountryDatabase.open(file),
                new GeoIpDownloader(URI.create("https://127.0.0.1:9/"), Runnable::run), clock,
                logger);
        if (!service.enable()) {
            throw new IllegalStateException("geoip.enabled is false in this configuration");
        }
        service.refresh();
        if (service.state() != CountryLookup.State.READY) {
            throw new IllegalStateException("the test database didn't load");
        }
        return service;
    }
}
