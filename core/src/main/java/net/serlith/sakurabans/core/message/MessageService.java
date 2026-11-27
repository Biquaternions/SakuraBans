package net.serlith.sakurabans.core.message;

import com.google.common.base.Preconditions;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.identity.Identity;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import net.kyori.adventure.text.minimessage.Context;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.ArgumentQueue;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.serlith.sakurabans.api.model.Scope;
import net.serlith.sakurabans.core.configuration.ConfigService;
import org.jspecify.annotations.Nullable;

public final class MessageService {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private static final Map<String, MessageKey> PALETTE = Map.of(
            "main", MessageKey.PALETTE_MAIN,
            "accent", MessageKey.PALETTE_ACCENT,
            "highlight", MessageKey.PALETTE_HIGHLIGHT,
            "danger", MessageKey.PALETTE_DANGER);

    public static final Set<String> STYLE_TAGS =
            Set.of("prefix", "plugin", "main", "accent", "highlight", "danger");

    private final Path dataDirectory;
    private final ConfigService config;
    private final ComponentLogger logger;
    private final NetworkTotals totals;
    private final AtomicReference<Map<Locale, Map<String, String>>> texts =
            new AtomicReference<>(Map.of(LangLoader.BASE_LOCALE, LangLoader.builtIn()));

    /** The overrides of every scope, and what they give this server (protocol 5.3). */
    private final AtomicReference<Map<Scope, Map<Locale, Map<String, String>>>> scoped =
            new AtomicReference<>(Map.of());
    private final AtomicReference<Resolved> resolved =
            new AtomicReference<>(new Resolved(List.of(), Map.of()));
    private final AtomicReference<@Nullable StyleCache> styleCache = new AtomicReference<>();

    /** The layers it was resolved for: a reload that changes them resolves again. */
    private record Resolved(List<Scope> layers, Map<Locale, Map<String, String>> overrides) {
    }

    private record StyleCache(Map<Locale, Map<String, String>> texts,
            Map<Locale, Map<String, String>> overrides, Locale defaultLocale,
            ConcurrentMap<Locale, TagResolver> byLocale) {
    }

    public MessageService(final Path dataDirectory, final ConfigService config,
            final ComponentLogger logger, final NetworkTotals totals) {
        this.dataDirectory = dataDirectory;
        this.config = config;
        this.logger = logger;
        this.totals = totals;
    }

    public void load() {
        final LangLoader.Loaded loaded = LangLoader.load(this.dataDirectory);
        loaded.problems().forEach(problem -> this.logger.warn("Messages: {}", problem));
        this.texts.set(Map.copyOf(loaded.texts()));
        loaded.texts().forEach((locale, values) -> PALETTE.values().forEach(key -> {
            final String text = values.get(key.key());
            if (text != null && parseColor(text) == null) {
                this.logger.warn("Messages: {} in {} is not a colour (#rrggbb or a name like"
                        + " light_purple); the built-in one is used", key.key(), locale);
            }
        }));
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
        final TagResolver styles = this.styles(locale);
        // The network's counts in every message.
        return MINI_MESSAGE.deserialize(text,
                TagResolver.resolver(styles, this.totals.tags(), TagResolver.resolver(resolvers)));
    }

    public boolean uses(final MessageKey key, final List<String> tags) {
        final List<String> texts = new ArrayList<>();
        this.texts.get().values().forEach(values -> texts.add(values.getOrDefault(key.key(), "")));
        this.scoped.get().values().forEach(locales -> locales.values()
                .forEach(values -> texts.add(values.getOrDefault(key.key(), ""))));
        return texts.stream().anyMatch(text -> tags.stream()
                .anyMatch(tag -> text.contains("<" + tag + ">")));
    }

    public TagResolver styles(final Locale locale) {
        final Map<Locale, Map<String, String>> texts = this.texts.get();
        final Map<Locale, Map<String, String>> overrides = this.overrides();
        final Locale defaultLocale = this.defaultLocale();
        StyleCache cache = this.styleCache.get();
        if (cache == null || cache.texts() != texts || cache.overrides() != overrides
                || !cache.defaultLocale().equals(defaultLocale)) {
            cache = new StyleCache(texts, overrides, defaultLocale, new ConcurrentHashMap<>());
            this.styleCache.set(cache);
        }
        return cache.byLocale().computeIfAbsent(locale, this::buildStyles);
    }

    private TagResolver buildStyles(final Locale locale) {
        final TagResolver.Builder palette = TagResolver.builder();
        PALETTE.forEach((name, key) -> palette.tag(name, Tag.styling(this.color(key, locale))));
        final TagResolver colors = palette.build();
        final TagResolver plugin = TagResolver.resolver("plugin", Tag.selfClosingInserting(
                MINI_MESSAGE.deserialize(this.text(MessageKey.PLUGIN, locale), colors)));
        final TagResolver prefix = TagResolver.resolver("prefix", Tag.selfClosingInserting(
                MINI_MESSAGE.deserialize(this.text(MessageKey.PREFIX, locale), colors, plugin)));
        return TagResolver.resolver(prefix, plugin, colors);
    }

    public TagResolver currentStyles() {
        return new TagResolver() {
            @Override
            public @Nullable Tag resolve(final String name, final ArgumentQueue arguments,
                    final Context ctx) {
                return MessageService.this.styles(MessageService.this.defaultLocale())
                        .resolve(name, arguments, ctx);
            }

            @Override
            public boolean has(final String name) {
                return STYLE_TAGS.contains(name);
            }
        };
    }

    /** A palette entry's colour; a text that isn't a colour gives the built-in one. */
    public TextColor color(final MessageKey key, final Locale locale) {
        final TextColor color = parseColor(this.text(key, locale));
        return color != null ? color
                : Preconditions.checkNotNull(parseColor(LangLoader.builtIn().get(key.key())));
    }

    static @Nullable TextColor parseColor(final @Nullable String text) {
        if (text == null) {
            return null;
        }
        final String value = text.strip();
        return value.startsWith("#") ? TextColor.fromHexString(value)
                : NamedTextColor.NAMES.value(value.toLowerCase(Locale.ROOT));
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
