package net.serlith.sakurabans.core.placeholder;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * A start without MiniPlaceholders never loads the bridge class, so nothing resolves the
 * plugin's classes. Our classes are loaded again by a loader that records them and that can't
 * see MiniPlaceholders, as on a server without it.
 */
class BridgeLoadingTest {

    private static final class RecordingLoader extends URLClassLoader {

        private final Set<String> loaded = ConcurrentHashMap.newKeySet();

        RecordingLoader(final URL[] urls, final ClassLoader parent) {
            super(urls, parent);
        }

        @Override
        protected Class<?> loadClass(final String name, final boolean resolve)
                throws ClassNotFoundException {
            synchronized (this.getClassLoadingLock(name)) {
                if (name.startsWith("io.github.miniplaceholders.")) {
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

    @Test
    void startWithoutThePluginLoadsNoBridgeClass() throws Exception {
        final URL main = PlaceholderBridges.class.getProtectionDomain().getCodeSource()
                .getLocation();
        final URL test = BridgeLoadingTest.class.getProtectionDomain().getCodeSource()
                .getLocation();
        try (RecordingLoader loader = new RecordingLoader(new URL[] {main, test},
                BridgeLoadingTest.class.getClassLoader())) {
            final Runnable probe = (Runnable) loader.loadClass(NoPluginProbe.class.getName())
                    .getDeclaredConstructor().newInstance();

            probe.run();

            Assertions.assertThat(loader.loaded)
                    .contains(PlaceholderBridges.class.getName())
                    .doesNotContain(MiniPlaceholdersBridge.class.getName())
                    .noneMatch(name -> name.startsWith("io.github.miniplaceholders."));
        }
    }

    @Test
    void theRecordingLoaderWouldSeeTheBridge() throws Exception {
        final URL main = PlaceholderBridges.class.getProtectionDomain().getCodeSource()
                .getLocation();
        try (RecordingLoader loader = new RecordingLoader(new URL[] {main},
                BridgeLoadingTest.class.getClassLoader())) {
            Assertions.assertThatThrownBy(() -> Class.forName(
                            MiniPlaceholdersBridge.class.getName(), false, loader)
                    .getDeclaredFields())
                    .as("without the plugin, the bridge can't link")
                    .isInstanceOf(LinkageError.class);
            Assertions.assertThat(List.copyOf(loader.loaded))
                    .contains(MiniPlaceholdersBridge.class.getName());
        }
    }
}
