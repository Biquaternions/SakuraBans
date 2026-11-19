package net.serlith.sakurabans.core.message;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

final class LangLoader {

    static final String DEFAULT_FILE = "en_US.yml";
    static final Locale BASE_LOCALE = Locale.US;

    private LangLoader() {
    }

    record Loaded(Map<Locale, Map<String, String>> texts, List<String> problems) {
    }

    static Loaded load(final Path dataDirectory) {
        final List<String> problems = new ArrayList<>();
        final Map<String, String> base = builtIn();
        final Map<Locale, Map<String, String>> texts = new LinkedHashMap<>();
        texts.put(BASE_LOCALE, new LinkedHashMap<>(base));
        final Path directory = dataDirectory.resolve("lang");
        try {
            Files.createDirectories(directory);
            final Path english = directory.resolve(DEFAULT_FILE);
            if (Files.notExists(english)) {
                try (InputStream input = resource()) {
                    Files.copy(input, english);
                }
            }
            try (Stream<Path> files = Files.list(directory)) {
                for (final Path file : files.filter(LangLoader::isLangFile).sorted().toList()) {
                    readFile(file, base, texts, problems);
                }
            }
        } catch (final IOException e) {
            problems.add("lang: " + e.getMessage());
        }
        return new Loaded(texts, List.copyOf(problems));
    }

    static Map<String, String> builtIn() {
        try (InputStream input = resource();
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(input, StandardCharsets.UTF_8))) {
            return flatten(YamlConfigurationLoader.builder().source(() -> reader).build().load());
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void readFile(final Path file, final Map<String, String> base,
            final Map<Locale, Map<String, String>> texts, final List<String> problems) {
        final String name = file.getFileName().toString();
        final Locale locale = Locale.forLanguageTag(
                name.substring(0, name.length() - ".yml".length()).replace('_', '-'));
        final Map<String, String> read;
        try {
            read = flatten(YamlConfigurationLoader.builder().path(file).build().load());
        } catch (final ConfigurateException e) {
            problems.add("lang/" + name + ": " + e.getMessage() + " (the file is ignored)");
            return;
        }
        final List<String> unknown = read.keySet().stream().filter(key -> !base.containsKey(key))
                .sorted().toList();
        if (!unknown.isEmpty()) {
            problems.add("lang/" + name + ": unknown keys, ignored: " + String.join(", ", unknown));
        }
        if (locale.equals(BASE_LOCALE)) {
            final List<String> missing = base.keySet().stream()
                    .filter(key -> !read.containsKey(key)).sorted().toList();
            if (!missing.isEmpty()) {
                problems.add("lang/" + name + ": missing keys use the built-in English text: "
                        + String.join(", ", missing));
            }
        }
        final Map<String, String> target =
                texts.computeIfAbsent(locale, ignored -> new LinkedHashMap<>());
        read.forEach((key, text) -> {
            if (base.containsKey(key)) {
                target.put(key, text);
            }
        });
    }

    private static Map<String, String> flatten(final ConfigurationNode root) {
        final Map<String, String> flat = new LinkedHashMap<>();
        flatten(root, "", flat);
        return flat;
    }

    private static void flatten(final ConfigurationNode node, final String path,
            final Map<String, String> flat) {
        if (node.isMap()) {
            node.childrenMap().forEach((key, child) -> flatten(child,
                    path.isEmpty() ? String.valueOf(key) : path + "." + key, flat));
        } else if (node.isList()) {
            // A list is a multi-line message (disconnect screens).
            flat.put(path, String.join("\n",
                    node.childrenList().stream().map(child -> child.getString("")).toList()));
        } else if (!node.isNull()) {
            flat.put(path, node.getString(""));
        }
    }

    private static boolean isLangFile(final Path file) {
        return Files.isRegularFile(file) && file.getFileName().toString().endsWith(".yml");
    }

    private static InputStream resource() {
        final InputStream input = LangLoader.class.getResourceAsStream("/lang/" + DEFAULT_FILE);
        if (input == null) {
            throw new UncheckedIOException(
                    new IOException("lang/en_US.yml is missing from the jar"));
        }
        return input;
    }
}
