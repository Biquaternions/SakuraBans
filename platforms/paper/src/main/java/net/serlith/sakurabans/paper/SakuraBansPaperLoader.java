package net.serlith.sakurabans.paper;

import io.papermc.paper.plugin.loader.PluginClasspathBuilder;
import io.papermc.paper.plugin.loader.PluginLoader;
import io.papermc.paper.plugin.loader.library.impl.MavenLibraryResolver;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.Exclusion;
import org.eclipse.aether.repository.RemoteRepository;

@SuppressWarnings("UnstableApiUsage")
public final class SakuraBansPaperLoader implements PluginLoader {

    private static final String LIBRARIES = "/paper-libraries.txt";

    /** Paper already provides these; resolving our own copies would shadow nothing but waste. */
    private static final List<Exclusion> PROVIDED = List.of(
            new Exclusion("net.kyori", "*", "*", "*"),
            new Exclusion("org.slf4j", "*", "*", "*"));

    public SakuraBansPaperLoader() {
    }

    @Override
    public void classloader(final PluginClasspathBuilder classpathBuilder) {
        final MavenLibraryResolver resolver = new MavenLibraryResolver();
        resolver.addRepository(new RemoteRepository.Builder(
                "central", "default", MavenLibraryResolver.MAVEN_CENTRAL_DEFAULT_MIRROR).build());
        for (final String coordinates : libraries()) {
            resolver.addDependency(
                    new Dependency(new DefaultArtifact(coordinates), null, false, PROVIDED));
        }
        classpathBuilder.addLibrary(resolver);
    }

    private static List<String> libraries() {
        try (InputStream input = SakuraBansPaperLoader.class.getResourceAsStream(LIBRARIES)) {
            if (input == null) {
                throw new IllegalStateException(LIBRARIES + " is missing from the plugin jar");
            }
            final BufferedReader reader =
                    new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
            return reader.lines().map(String::strip).filter(line -> !line.isEmpty()).toList();
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
