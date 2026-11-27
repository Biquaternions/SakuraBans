package net.serlith.sakurabans.core.geoip;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.Path;
import java.util.Optional;

public interface CountryDatabase {

    Optional<Country> country(InetAddress address) throws IOException;

    void close();

    @FunctionalInterface
    interface Opener {

        CountryDatabase open(Path file) throws IOException;
    }

}
