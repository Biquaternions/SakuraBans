package net.serlith.sakurabans.core.geoip;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import net.serlith.sakurabans.core.bootstrap.GeoIpOffProbe;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * S72: with GeoIP off, no class of MaxMind's reader and not {@link MaxMindCountryDatabase} is
 * loaded. Our classes are loaded again by a loader that records them and that can't see
 * {@code com.maxmind}, as on a server where the library is missing.
 */
class GeoIpLoadingTest {

    @TempDir
    Path dir;

    private static final class RecordingLoader extends URLClassLoader {

        private final Set<String> loaded = ConcurrentHashMap.newKeySet();

        RecordingLoader(final URL[] urls, final ClassLoader parent) {
            super(urls, parent);
        }

        @Override
        protected Class<?> loadClass(final String name, final boolean resolve)
                throws ClassNotFoundException {
            synchronized (this.getClassLoadingLock(name)) {
                if (name.startsWith("com.maxmind.")) {
                    this.loaded.add(name);
                    throw new ClassNotFoundException(name);
                }
                if (!name.startsWith("net.serlith.sakurabans.core.")) {
                    return super.loadClass(name, resolve);
                }
                Class<?> found = this.findLoadedClass(name);
                if (found == null) {
                    found = this.findClass(name);
                    this.loaded.add(name);
                }
                if (resolve) {
                    this.resolveClass(found);
                }
                return found;
            }
        }
    }

    private static URL[] classpath() {
        return new URL[] {
            GeoIpService.class.getProtectionDomain().getCodeSource().getLocation(),
            GeoIpLoadingTest.class.getProtectionDomain().getCodeSource().getLocation()};
    }

    @Test
    void theCoreWithGeoIpOffLoadsNoReaderClass() throws Exception {
        try (RecordingLoader loader = new RecordingLoader(classpath(),
                GeoIpLoadingTest.class.getClassLoader())) {
            @SuppressWarnings("unchecked")
            final Consumer<Path> probe = (Consumer<Path>) loader.loadClass(
                    GeoIpOffProbe.class.getName()).getDeclaredConstructor().newInstance();

            probe.accept(this.dir);

            Assertions.assertThat(loader.loaded)
                    .contains(GeoIpService.class.getName(),
                            "net.serlith.sakurabans.core.bootstrap.CoreComponents")
                    .doesNotContain(MaxMindCountryDatabase.class.getName())
                    .noneMatch(name -> name.startsWith("com.maxmind."));
        }
    }

    @Test
    void withoutTheLibraryGeoIpTurnsOffWithOneWarning() throws Exception {
        GeoIpTestFiles.place(this.dir);
        try (RecordingLoader loader = new RecordingLoader(classpath(),
                GeoIpLoadingTest.class.getClassLoader())) {
            @SuppressWarnings("unchecked")
            final Consumer<Path> probe = (Consumer<Path>) loader.loadClass(
                    NoLibraryProbe.class.getName()).getDeclaredConstructor().newInstance();

            probe.accept(this.dir);

            Assertions.assertThat(List.copyOf(loader.loaded))
                    .contains(MaxMindCountryDatabase.class.getName())
                    .anyMatch(name -> name.startsWith("com.maxmind."));
        }
    }
}
