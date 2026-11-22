package net.serlith.sakurabans.core.message;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
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

    public MessageService(final Path dataDirectory, final ConfigService config,
            final ComponentLogger logger) {
        this.dataDirectory = dataDirectory;
        this.config = config;
        this.logger = logger;
    }

    /** Reads the language files; problems are logged, never fatal. Also used by reload. */
    public void load() {
        final LangLoader.Loaded loaded = LangLoader.load(this.dataDirectory);
        loaded.problems().forEach(problem -> this.logger.warn("Messages: {}", problem));
        this.texts.set(Map.copyOf(loaded.texts()));
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

    /** Sends in the audience's own locale, or the configured default when it has none. */
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

    /**
     * The raw text: the exact locale, then any file of the same language, then the configured
     * default locale, then English.
     */
    String text(final MessageKey key, final Locale locale) {
        return this.find(key.key(), locale).orElseThrow(
                () -> new IllegalStateException("no text for " + key.key()));
    }

    /** {@link #text(MessageKey, Locale)} for a key given as text (Cloud captions). */
    public Optional<String> find(final String key, final Locale locale) {
        final Map<Locale, Map<String, String>> all = this.texts.get();
        final String exact = lookup(all.get(locale), key);
        if (exact != null) {
            return Optional.of(exact);
        }
        for (final Map.Entry<Locale, Map<String, String>> entry : all.entrySet()) {
            if (entry.getKey().getLanguage().equals(locale.getLanguage())) {
                final String sameLanguage = lookup(entry.getValue(), key);
                if (sameLanguage != null) {
                    return Optional.of(sameLanguage);
                }
            }
        }
        final String fallback = lookup(all.get(this.defaultLocale()), key);
        if (fallback != null) {
            return Optional.of(fallback);
        }
        return Optional.ofNullable(lookup(all.get(LangLoader.BASE_LOCALE), key));
    }

    private static @Nullable String lookup(
            final @Nullable Map<String, String> texts, final String key) {
        return texts == null ? null : texts.get(key);
    }
}
