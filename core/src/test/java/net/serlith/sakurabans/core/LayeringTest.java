package net.serlith.sakurabans.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Checks the package layering of {@code core}.
 *
 * <p>A package may import packages of a lower layer, and packages of its own layer that are
 * listed before it. Sub-packages count as their top-level package. The root package is written
 * as {@code ""}.
 */
class LayeringTest {

    private static final List<List<String>> LAYERS = List.of(
            List.of("util", "concurrent", "model", "platform"),
            List.of("configuration", "message", "storage", "geoip"),
            List.of("permission", "event", "sync", "player", "scope"),
            List.of("template", "punish", "alts", "limit", "notify",
                    "admin", "data"),
            List.of("enforce", "importer", "placeholder"),
            List.of("command"),
            List.of("", "bootstrap"));

    private static final String ROOT = "net.serlith.sakurabans.core";
    private static final Path SOURCES = Path.of("src/main/java/net/serlith/sakurabans/core");
    private static final Pattern IMPORT = Pattern.compile(
            "^import\\s+(?:static\\s+)?" + Pattern.quote(ROOT) + "\\.(\\w+)", Pattern.MULTILINE);

    private static final Map<String, Integer> RANKS = ranks();

    @Test
    void sourcesExist() {
        assertThat(SOURCES).isDirectory();
    }

    @Test
    void everyPackageIsInTheLayerTable() throws IOException {
        final List<String> unknown = new ArrayList<>();
        for (final Path file : sourceFiles()) {
            final String layer = topLevelPackage(file);
            if (!RANKS.containsKey(layer)) {
                unknown.add(layer + " (" + SOURCES.relativize(file) + ")");
            }
        }
        assertThat(unknown)
                .as("packages missing from the layer table (LayeringTest.LAYERS)")
                .isEmpty();
    }

    @Test
    void importsOnlyGoDownOrSideways() throws IOException {
        final List<String> violations = new ArrayList<>();
        for (final Path file : sourceFiles()) {
            final String from = topLevelPackage(file);
            final Matcher matcher = IMPORT.matcher(read(file));
            while (matcher.find()) {
                final String to = importedTopLevelPackage(matcher.group(1));
                if (!allowed(from, to)) {
                    violations.add(
                            SOURCES.relativize(file) + ": '" + from + "' imports '" + to + "'");
                }
            }
        }
        assertThat(violations).as("imports that break the layering").isEmpty();
    }

    @Test
    void rulesFollowTheTable() {
        assertThat(allowed("storage", "util")).isTrue();
        assertThat(allowed("storage", "configuration")).isTrue();
        assertThat(allowed("configuration", "storage")).isFalse();
        assertThat(allowed("util", "platform")).isFalse();
        assertThat(allowed("bootstrap", "")).isTrue();
        assertThat(allowed("", "bootstrap")).isFalse();
        assertThat(allowed("sync", "punish")).isFalse();
    }

    private static boolean allowed(final String from, final String to) {
        if (from.equals(to)) {
            return true;
        }
        final Integer fromRank = RANKS.get(from);
        final Integer toRank = RANKS.get(to);
        // Unknown packages are reported by everyPackageIsInTheLayerTable.
        return fromRank == null || toRank == null || toRank < fromRank;
    }

    private static String importedTopLevelPackage(final String firstSegment) {
        // net.serlith.sakurabans.core.SakuraBansCore is a class of the root package.
        return Character.isUpperCase(firstSegment.charAt(0)) ? "" : firstSegment;
    }

    private static String topLevelPackage(final Path file) {
        final Path relative = SOURCES.relativize(file);
        return relative.getNameCount() == 1 ? "" : relative.getName(0).toString();
    }

    private static List<Path> sourceFiles() throws IOException {
        try (Stream<Path> files = Files.walk(SOURCES)) {
            return files.filter(path -> path.toString().endsWith(".java")).toList();
        }
    }

    private static String read(final Path file) {
        try {
            return Files.readString(file);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Map<String, Integer> ranks() {
        final Map<String, Integer> ranks = new HashMap<>();
        int rank = 0;
        for (final List<String> layer : LAYERS) {
            for (final String pkg : layer) {
                ranks.put(pkg, rank++);
            }
        }
        return Map.copyOf(ranks);
    }
}
