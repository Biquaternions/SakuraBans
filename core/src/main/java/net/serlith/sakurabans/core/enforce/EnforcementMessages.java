package net.serlith.sakurabans.core.enforce;

import java.time.InstantSource;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.serlith.sakurabans.api.model.Punishment;
import net.serlith.sakurabans.core.message.MessageKey;
import net.serlith.sakurabans.core.message.MessageService;
import net.serlith.sakurabans.core.message.Placeholders;
import net.serlith.sakurabans.core.platform.OnlinePlayer;
import net.serlith.sakurabans.core.player.NameCache;

public final class EnforcementMessages {

    private final MessageService messages;
    private final Placeholders placeholders;
    private final NameCache names;
    private final InstantSource clock;

    public EnforcementMessages(final MessageService messages, final Placeholders placeholders,
            final NameCache names, final InstantSource clock) {
        this.messages = messages;
        this.placeholders = placeholders;
        this.names = names;
        this.clock = clock;
    }

    public Component banScreen(final Punishment ban, final Locale locale) {
        return this.render(ban.permanent() ? MessageKey.SCREEN_BAN
                : MessageKey.SCREEN_BAN_TEMPORARY, ban, locale);
    }

    public Component kickScreen(final Punishment kick, final Locale locale) {
        return this.render(MessageKey.SCREEN_KICK, kick, locale);
    }

    public Component muted(final Punishment mute, final Locale locale) {
        return this.render(mute.permanent() ? MessageKey.VICTIM_MUTED
                : MessageKey.VICTIM_MUTED_TEMPORARY, mute, locale);
    }

    public Component warned(final Punishment warn, final Locale locale) {
        return this.render(MessageKey.VICTIM_WARNED, warn, locale);
    }

    public Component chatMuted(final Punishment mute, final Locale locale) {
        return this.render(mute.permanent() ? MessageKey.CHAT_MUTED
                : MessageKey.CHAT_MUTED_TEMPORARY, mute, locale);
    }

    public Component commandMuted(final Punishment mute, final Locale locale) {
        return this.render(MessageKey.CHAT_COMMAND_MUTED, mute, locale);
    }

    public Component connectionLimit(final Locale locale) {
        return this.messages.render(MessageKey.SCREEN_CONNECTION_LIMIT, locale);
    }

    public Component checkFailed(final Locale locale) {
        return this.messages.render(MessageKey.SCREEN_CHECK_FAILED, locale);
    }

    public Component loading(final Locale locale) {
        return this.messages.render(MessageKey.CHAT_LOADING, locale);
    }

    public Locale localeOf(final OnlinePlayer player) {
        return player.locale().orElseGet(this.messages::defaultLocale);
    }

    public Locale defaultLocale() {
        return this.messages.defaultLocale();
    }

    private Component render(final MessageKey key, final Punishment punishment,
            final Locale locale) {
        return this.messages.render(key, locale, this.placeholders.punishment(punishment, locale,
                this.clock.instant(), this.names::name, false));
    }

}
