package net.serlith.sakurabans.paper;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.serlith.sakurabans.core.placeholder.MiniPlaceholdersBridge;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * A Paper start without PlaceholderAPI and MiniPlaceholders loads neither bridge class,
 * through a loader that records our classes and can't see the plugins' ones.
 */
class PaperPlaceholdersLoadingTest {

    private static final class RecordingLoader extends URLClassLoader {

        private final Set<String> loaded = ConcurrentHashMap.newKeySet();

        RecordingLoader(final URL[] urls, final ClassLoader parent) {
            super(urls, parent);
        }

        @Override
        protected Class<?> loadClass(final String name, final boolean resolve)
                throws ClassNotFoundException {
            synchronized (this.getClassLoadingLock(name)) {
                if (name.startsWith("me.clip.") || name.startsWith("io.github.miniplaceholders.")
                        || name.equals(MiniPlaceholdersBridge.class.getName())) {
                    this.loaded.add(name);
                    throw new ClassNotFoundException(name);
                }
                if (!name.startsWith("net.serlith.sakurabans.paper.")) {
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
    void neitherBridgeIsLoaded() throws Exception {
        final URL main = PaperPlaceholders.class.getProtectionDomain().getCodeSource()
                .getLocation();
        final URL test = PaperPlaceholdersLoadingTest.class.getProtectionDomain()
                .getCodeSource().getLocation();
        try (RecordingLoader loader = new RecordingLoader(new URL[] {main, test},
                PaperPlaceholdersLoadingTest.class.getClassLoader())) {
            ((Runnable) loader.loadClass(NoPlaceholderPluginsProbe.class.getName())
                    .getDeclaredConstructor().newInstance()).run();

            Assertions.assertThat(loader.loaded)
                    .contains(PaperPlaceholders.class.getName())
                    .doesNotContain(PaperPlaceholderExpansion.class.getName(),
                            MiniPlaceholdersBridge.class.getName())
                    .noneMatch(name -> name.startsWith("me.clip.")
                            || name.startsWith("io.github.miniplaceholders."));
        }
    }
}
