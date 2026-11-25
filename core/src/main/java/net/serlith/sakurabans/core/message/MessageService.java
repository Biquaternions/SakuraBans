package net.serlith.sakurabans.core.message;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashSet;
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
import net.serlith.sakurabans.core.configuration.ConfigService;
import org.jspecify.annotations.Nullable;

public final class MessageService {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private final Path dataDirectory;
    private final ConfigService config;
    private final ComponentLogger logger;
    private final AtomicReference<Map<Locale, Map<String, String>>> texts =
            new AtomicReference<>(Map.of(LangLoader.BASE_LOCALE, LangLoader.builtIn()));

    private final AtomicReference<Map<Locale, Map<String, String>>> overrides =
            new AtomicReference<>(Map.of());

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

    public void overrides(final Map<Locale, Map<String, String>> overrides) {
        final Map<Locale, Map<String, String>> copy = new HashMap<>();
        overrides.forEach((locale, texts) -> copy.put(locale, Map.copyOf(texts)));
        this.overrides.set(Map.copyOf(copy));
    }

    public Map<Locale, Map<String, String>> overrides() {
        return this.overrides.get();
    }

    public Optional<String> fileText(final String key, final Locale locale) {
        return Optional.ofNullable(lookup(this.texts.get().get(locale), key));
    }

    public static Map<String, String> builtInTexts() {
        return LangLoader.builtIn();
    }

    public Component render(final MessageKey key, final Locale locale,
            final TagResolver... resolvers) {
        final TagResolver prefix = TagResolver.resolver("prefix", Tag.selfClosingInserting(
                MINI_MESSAGE.deserialize(this.text(MessageKey.PREFIX, locale))));
        return MINI_MESSAGE.deserialize(this.text(key, locale),
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
        final Map<Locale, Map<String, String>> overridden = this.overrides.get();
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
