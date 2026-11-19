package net.serlith.sakurabans.api.format;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.serlith.sakurabans.api.model.Punishment;

/**
 * Renders punishments with the configured messages ({@code lang/<locale>.yml}), so other plugins
 * show them the way SakuraBans does. Implemented by SakuraBans; don't implement it.
 *
 * <p>Every method renders from the loaded messages, without I/O, on any thread. A locale without
 * its own messages falls back to {@code en_US}.
 */
public interface MessageFormatter {

    /**
     * The screen a player sees when this punishment denies or ends their connection (bans and
     * kicks).
     *
     * @param punishment the punishment
     * @param locale the locale to render in
     * @return the screen
     */
    Component disconnectScreen(Punishment punishment, Locale locale);

    /**
     * A one-line description, as used in history and list commands.
     *
     * @param punishment the punishment
     * @param locale the locale to render in
     * @return the description
     */
    Component summary(Punishment punishment, Locale locale);

    /**
     * Formats a duration the way messages show it, for example {@code 1 day 12 hours}.
     *
     * @param duration the duration
     * @param locale the locale to render in
     * @return the text
     */
    String formatDuration(Duration duration, Locale locale);

    /**
     * Formats an instant with the configured date format and time zone.
     *
     * @param instant the instant
     * @param locale the locale to render in
     * @return the text
     */
    String formatInstant(Instant instant, Locale locale);
}
