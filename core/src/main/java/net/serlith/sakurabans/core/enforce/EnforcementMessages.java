package net.serlith.sakurabans.core.enforce;

import java.time.InstantSource;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.serlith.sakurabans.api.model.Punishment;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.model.TemplateRef;
import net.serlith.sakurabans.api.template.LadderTemplate;
import net.serlith.sakurabans.api.template.TemplateDisplay;
import net.serlith.sakurabans.api.template.TemplateInfo;
import net.serlith.sakurabans.core.message.MessageKey;
import net.serlith.sakurabans.core.message.MessageService;
import net.serlith.sakurabans.core.message.Placeholders;
import net.serlith.sakurabans.core.model.Lockdown;
import net.serlith.sakurabans.core.platform.OnlinePlayer;
import net.serlith.sakurabans.core.player.NameCache;

public final class EnforcementMessages {

    private final MessageService messages;
    private final Placeholders placeholders;
    private final NameCache names;
    private final Function<TemplateRef, Optional<TemplateInfo>> templates;
    private final InstantSource clock;

    /**
     * {@code templates}: the definition a punishment came from, in memory (no database
     * access): its variant, else the global definition (S62).
     */
    public EnforcementMessages(final MessageService messages, final Placeholders placeholders,
            final NameCache names, final Function<TemplateRef, Optional<TemplateInfo>> templates,
            final InstantSource clock) {
        this.messages = messages;
        this.placeholders = placeholders;
        this.names = names;
        this.templates = templates;
        this.clock = clock;
    }

    public Component banScreen(final Punishment ban, final Locale locale) {
        return this.victim(ban.permanent() ? MessageKey.SCREEN_BAN
                : MessageKey.SCREEN_BAN_TEMPORARY, ban, locale);
    }

    public Component kickScreen(final Punishment kick, final Locale locale) {
        return this.victim(MessageKey.SCREEN_KICK, kick, locale);
    }

    public Component muted(final Punishment mute, final Locale locale) {
        return this.victim(mute.permanent() ? MessageKey.VICTIM_MUTED
                : MessageKey.VICTIM_MUTED_TEMPORARY, mute, locale);
    }

    public Component warned(final Punishment warn, final Locale locale) {
        return this.victim(MessageKey.VICTIM_WARNED, warn, locale);
    }

    public Component summary(final Punishment punishment, final Locale locale) {
        return this.render(MessageKey.CHECK_DETAIL, punishment, locale);
    }

    public Component warnedAway(final Punishment warn, final Locale locale) {
        return this.victim(MessageKey.VICTIM_WARNED_AWAY, warn, locale);
    }

    /**
     * How the mute notice or warning reaches the player: the template's display, chat without
     * a template or when it was deleted. The chat and command refusals are always chat.
     */
    public TemplateDisplay display(final Punishment punishment) {
        return punishment.template().flatMap(this.templates)
                .map(TemplateInfo::display)
                .orElse(TemplateDisplay.CHAT);
    }

    /** The dialog's title for a mute notice or a warning. */
    public Component noticeTitle(final Punishment punishment, final Locale locale) {
        return this.render(punishment.type() == PunishmentType.MUTE
                ? MessageKey.DIALOG_MUTED_TITLE : MessageKey.DIALOG_WARNED_TITLE,
                punishment, locale);
    }

    public Component noticeButton(final Locale locale) {
        return this.messages.render(MessageKey.DIALOG_CLOSE, locale);
    }

    public Component chatMuted(final Punishment mute, final Locale locale) {
        return this.victim(mute.permanent() ? MessageKey.CHAT_MUTED
                : MessageKey.CHAT_MUTED_TEMPORARY, mute, locale);
    }

    public Component commandMuted(final Punishment mute, final Locale locale) {
        return this.render(MessageKey.CHAT_COMMAND_MUTED, mute, locale);
    }

    public Component shadowEcho(final String name, final String message, final Locale locale) {
        return this.messages.render(MessageKey.CHAT_SHADOW_ECHO, locale,
                Placeholder.unparsed("player", name), Placeholder.unparsed("message", message));
    }

    public Component lockdownScreen(final Lockdown lockdown, final Locale locale) {
        return this.messages.render(MessageKey.SCREEN_LOCKDOWN, locale,
                Placeholder.unparsed("reason", lockdown.reason()));
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

    /**
     * What the punished player is shown: the template step's message, else the template's,
     * else the network's message for the key, from the variant the punishment came from on
     * every server. A deleted variant falls back to the global template, a deleted template to
     * the network's message.
     */
    private Component victim(final MessageKey key, final Punishment punishment,
            final Locale locale) {
        final String text = punishment.template().flatMap(ref -> this.templates.apply(ref)
                .flatMap(template -> stepMessage(template, ref).or(template::message)))
                .orElse(null);
        if (text == null) {
            return this.render(key, punishment, locale);
        }
        return this.messages.renderText(text, locale, this.placeholders.punishment(punishment,
                locale, this.clock.instant(), this.names::name, false));
    }

    private static Optional<String> stepMessage(final TemplateInfo template,
            final TemplateRef ref) {
        return template instanceof LadderTemplate ladder && ref.step().isPresent()
                ? ladder.step(ref.step().getAsInt()).message()
                : Optional.empty();
    }

    private Component render(final MessageKey key, final Punishment punishment,
            final Locale locale) {
        return this.messages.render(key, locale, this.placeholders.punishment(punishment, locale,
                this.clock.instant(), this.names::name, false));
    }

}
