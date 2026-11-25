package net.serlith.sakurabans.api.message;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.kyori.adventure.text.Component;
import net.serlith.sakurabans.api.admin.AdminResult;
import net.serlith.sakurabans.api.model.Actor;

/**
 * The message templates. The effective text is the database override if one is set, otherwise
 * the language file's text, otherwise the shipped English text. Reads and previews answer from
 * memory on any thread; changes are stored, audited and synced. Implemented by SakuraBans; don't
 * implement it.
 */
public interface MessageTemplateService {

    /**
     * Every message SakuraBans shows.
     *
     * @return the schema, by key
     */
    List<MessageField> schema();

    /**
     * The effective messages of a locale.
     *
     * @param locale the locale; one without its own messages falls back to {@code en_US}
     * @return the current snapshot
     */
    MessageSnapshot messages(Locale locale);

    /**
     * Overrides messages of a locale.
     *
     * @param locale the locale
     * @param patch the new MiniMessage texts by key
     * @param expectedVersion the snapshot version the caller last saw
     * @param actor who changes them
     * @return the new snapshot; {@code ValidationFailed} with the key as path for unknown keys
     *         and blank or too long texts. The MiniMessage itself is not checked: tags are the
     *         caller's responsibility, and an unknown tag renders as plain text
     */
    CompletableFuture<AdminResult<MessageSnapshot>> update(
            Locale locale, Map<String, String> patch, long expectedVersion, Actor actor);

    /**
     * Removes overrides of a locale.
     *
     * @param locale the locale
     * @param keys the keys to reset
     * @param expectedVersion the snapshot version the caller last saw
     * @param actor who resets them
     * @return the new snapshot
     */
    CompletableFuture<AdminResult<MessageSnapshot>> reset(
            Locale locale, Set<String> keys, long expectedVersion, Actor actor);

    /**
     * Renders a text as SakuraBans would, for a live preview while editing. User-supplied
     * placeholder values are inserted as plain text.
     *
     * @param text the MiniMessage text to render, which need not be stored
     * @param sample placeholder values by name
     * @return the rendered component; invalid MiniMessage renders as plain text
     */
    Component preview(String text, Map<String, String> sample);
}
