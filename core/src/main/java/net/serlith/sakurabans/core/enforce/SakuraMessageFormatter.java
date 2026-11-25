package net.serlith.sakurabans.core.enforce;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.serlith.sakurabans.api.format.MessageFormatter;
import net.serlith.sakurabans.api.model.Punishment;
import net.serlith.sakurabans.core.message.Formats;

public final class SakuraMessageFormatter implements MessageFormatter {

    private final EnforcementMessages messages;
    private final Formats formats;

    public SakuraMessageFormatter(final EnforcementMessages messages, final Formats formats) {
        this.messages = messages;
        this.formats = formats;
    }

    @Override
    public Component disconnectScreen(final Punishment punishment, final Locale locale) {
        return switch (punishment.type()) {
            case BAN -> this.messages.banScreen(punishment, locale);
            case KICK -> this.messages.kickScreen(punishment, locale);
            case MUTE -> this.messages.muted(punishment, locale);
            case WARN -> this.messages.warned(punishment, locale);
        };
    }

    @Override
    public Component summary(final Punishment punishment, final Locale locale) {
        return this.messages.summary(punishment, locale);
    }

    @Override
    public String formatDuration(final Duration duration, final Locale locale) {
        return this.formats.duration(duration, locale);
    }

    @Override
    public String formatInstant(final Instant instant, final Locale locale) {
        return this.formats.instant(instant, locale);
    }

}
