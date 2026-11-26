package net.serlith.sakurabans.core.message;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.identity.Identity;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.serlith.sakurabans.api.model.Scope;
import net.serlith.sakurabans.core.configuration.ConfigService;
import org.jspecify.annotations.Nullable;

public final class MessageService {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private final Path dataDirectory;
    private final ConfigService config;
    private final ComponentLogger logger;
    private final AtomicReference<Map<Locale, Map<String, String>>> texts =
            new AtomicReference<>(Map.of(LangLoader.BASE_LOCALE, LangLoader.builtIn()));

    /** The overrides of every scope, and what they give this server (protocol 5.3). */
    private final AtomicReference<Map<Scope, Map<Locale, Map<String, String>>>> scoped =
            new AtomicReference<>(Map.of());
    private final AtomicReference<Resolved> resolved =
            new AtomicReference<>(new Resolved(List.of(), Map.of()));

    /** The layers it was resolved for: a reload that changes them resolves again. */
    private record Resolved(List<Scope> layers, Map<Locale, Map<String, String>> overrides) {
    }

    public MessageService(final Path dataDirectory, final ConfigService config,
            final ComponentLogger logger) {
        this.dataDirectory = dataDirectory;
        this.config = config;
        this.logger = logger;
    }

    public void load() {
        final LangLoader.Loaded loaded = LangLoader.load(this.dataDirectory);
        loaded.problems().forEach(problem -> this.logger.warn("Messages: {}", problem));
        this.texts.set(Map.copyOf(loaded.texts()));
    }

    public void overrides(final Map<Scope, Map<Locale, Map<String, String>>> overrides) {
        final Map<Scope, Map<Locale, Map<String, String>>> copy = new HashMap<>();
        overrides.forEach((scope, locales) -> {
            final Map<Locale, Map<String, String>> texts = new HashMap<>();
            locales.forEach((locale, values) -> texts.put(locale, Map.copyOf(values)));
            copy.put(scope, Map.copyOf(texts));
        });
        this.scoped.set(Map.copyOf(copy));
        this.resolved.set(this.resolve(this.config.layers()));
    }

    /** The overrides that apply to this server, by locale: the first layer with a text wins. */
    public Map<Locale, Map<String, String>> overrides() {
        final List<Scope> layers = this.config.layers();
        Resolved current = this.resolved.get();
        if (!current.layers().equals(layers)) {
            current = this.resolve(layers);
            this.resolved.set(current);
        }
        return current.overrides();
    }

    /** Every scope's overrides. */
    public Map<Scope, Map<Locale, Map<String, String>>> scopedOverrides() {
        return this.scoped.get();
    }

    /** The scope whose override gives a key its text here in a locale, if one does. */
    public Optional<Scope> source(final String key, final Locale locale) {
        final Map<Scope, Map<Locale, Map<String, String>>> all = this.scoped.get();
        for (final Scope scope : this.config.layers()) {
            if (all.getOrDefault(scope, Map.of()).getOrDefault(locale, Map.of())
                    .containsKey(key)) {
                return Optional.of(scope);
            }
        }
        return Optional.empty();
    }

    private Resolved resolve(final List<Scope> layers) {
        final Map<Scope, Map<Locale, Map<String, String>>> all = this.scoped.get();
        final Map<Locale, Map<String, String>> merged = new HashMap<>();
        for (int i = layers.size() - 1; i >= 0; i--) {
            all.getOrDefault(layers.get(i), Map.of()).forEach((locale, texts) ->
                    merged.computeIfAbsent(locale, ignored -> new HashMap<>()).putAll(texts));
        }
        final Map<Locale, Map<String, String>> copy = new HashMap<>();
        merged.forEach((locale, texts) -> copy.put(locale, Map.copyOf(texts)));
        return new Resolved(List.copyOf(layers), Map.copyOf(copy));
    }

    public Optional<String> fileText(final String key, final Locale locale) {
        return Optional.ofNullable(lookup(this.texts.get().get(locale), key));
    }

    public Set<Locale> fileLocales() {
        return Set.copyOf(this.texts.get().keySet());
    }

    public static Map<String, String> builtInTexts() {
        return LangLoader.builtIn();
    }

    public Component render(final MessageKey key, final Locale locale,
            final TagResolver... resolvers) {
        return this.renderText(this.text(key, locale), locale, resolvers);
    }

    /** A text that isn't a message key (a template's message), with the same resolvers. */
    public Component renderText(final String text, final Locale locale,
            final TagResolver... resolvers) {
        final TagResolver prefix = TagResolver.resolver("prefix", Tag.selfClosingInserting(
                MINI_MESSAGE.deserialize(this.text(MessageKey.PREFIX, locale))));
        return MINI_MESSAGE.deserialize(text,
                TagResolver.resolver(prefix, TagResolver.resolver(resolvers)));
    }

    public String renderPlain(final MessageKey key, final Locale locale,
            final TagResolver... resolvers) {
        return PlainTextComponentSerializer.plainText().serialize(
                this.render(key, locale, resolvers));
    }

    public void send(final Audience audience, final MessageKey key,
            final TagResolver... resolvers) {
        audience.sendMessage(this.render(key, this.localeOf(audience), resolvers));
    }

    public Locale localeOf(final Audience audience) {
        return audience.get(Identity.LOCALE).orElseGet(this::defaultLocale);
    }

    public Locale defaultLocale() {
        return this.config.settings().display().defaultLocale();
    }

    String text(final MessageKey key, final Locale locale) {
        return this.find(key.key(), locale).orElseThrow(
                () -> new IllegalStateException("no text for " + key.key()));
    }

    public Optional<String> find(final String key, final Locale locale) {
        final Map<Locale, Map<String, String>> all = this.texts.get();
        final Map<Locale, Map<String, String>> overridden = this.overrides();
        final String exact = this.lookupAt(overridden, all, locale, key);
        if (exact != null) {
            return Optional.of(exact);
        }
        for (final Locale other : this.locales(overridden, all)) {
            if (other.getLanguage().equals(locale.getLanguage())) {
                final String sameLanguage = this.lookupAt(overridden, all, other, key);
                if (sameLanguage != null) {
                    return Optional.of(sameLanguage);
                }
            }
        }
        final String fallback = this.lookupAt(overridden, all, this.defaultLocale(), key);
        if (fallback != null) {
            return Optional.of(fallback);
        }
        return Optional.ofNullable(this.lookupAt(overridden, all, LangLoader.BASE_LOCALE, key));
    }

    private @Nullable String lookupAt(final Map<Locale, Map<String, String>> overridden,
            final Map<Locale, Map<String, String>> all, final Locale locale, final String key) {
        final String override = lookup(overridden.get(locale), key);
        return override != null ? override : lookup(all.get(locale), key);
    }

    private Set<Locale> locales(final Map<Locale, Map<String, String>> overridden,
            final Map<Locale, Map<String, String>> all) {
        final Set<Locale> locales = new LinkedHashSet<>(overridden.keySet());
        locales.addAll(all.keySet());
        return locales;
    }

    private static @Nullable String lookup(
            final @Nullable Map<String, String> texts, final String key) {
        return texts == null ? null : texts.get(key);
    }
}
