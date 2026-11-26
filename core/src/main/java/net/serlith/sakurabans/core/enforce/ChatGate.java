package net.serlith.sakurabans.core.enforce;

import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.concurrent.atomic.AtomicReference;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import net.serlith.sakurabans.api.event.PlayerDenyEvent.Denial;
import net.serlith.sakurabans.api.model.Punishment;
import net.serlith.sakurabans.core.event.SakuraEventBus;
import net.serlith.sakurabans.core.event.SakuraPlayerDenyEvent;
import net.serlith.sakurabans.core.notify.NotificationService;
import net.serlith.sakurabans.core.platform.OnlinePlayer;
import net.serlith.sakurabans.core.punish.MuteCache;
import net.serlith.sakurabans.core.util.Futures;

public final class ChatGate {

    private static final Duration PASSED_WARNING_INTERVAL = Duration.ofMinutes(1);

    private final MuteCache mutes;
    private final CommandBlacklist blacklist;
    private final EnforcementMessages messages;
    private final NotificationService notifications;
    private final SakuraEventBus events;
    private final InstantSource clock;
    private final ComponentLogger logger;
    private final AtomicReference<Instant> lastPassedWarning =
            new AtomicReference<>(Instant.MIN);

    public ChatGate(final MuteCache mutes, final CommandBlacklist blacklist,
            final EnforcementMessages messages, final NotificationService notifications,
            final SakuraEventBus events, final InstantSource clock,
            final ComponentLogger logger) {
        this.mutes = mutes;
        this.blacklist = blacklist;
        this.messages = messages;
        this.notifications = notifications;
        this.events = events;
        this.clock = clock;
        this.logger = logger;
    }

    public ChatDecision checkChat(final OnlinePlayer player, final String message) {
        return this.checkChat(player, message, true);
    }

    public ChatDecision checkChat(final OnlinePlayer player, final String message,
            final boolean cancellable) {
        return this.check(player, message, false, cancellable);
    }

    public ChatDecision checkCommand(final OnlinePlayer player, final String commandLine) {
        return this.checkCommand(player, commandLine, true);
    }

    public ChatDecision checkCommand(final OnlinePlayer player, final String commandLine,
            final boolean cancellable) {
        if (!this.blacklist.blocked(commandLine)) {
            return new ChatDecision.Allowed();
        }
        return this.check(player, commandLine, true, cancellable);
    }

    public Component shadowEcho(final OnlinePlayer player, final String message) {
        return this.messages.shadowEcho(player.name(), message, this.messages.localeOf(player));
    }

    private ChatDecision check(final OnlinePlayer player, final String text,
            final boolean command, final boolean cancellable) {
        return switch (this.mutes.lookup(player)) {
            case MuteCache.Lookup.NotMuted notMuted -> new ChatDecision.Allowed();
            case MuteCache.Lookup.Unknown unknown -> {
                // Shouldn't happen after a login; deny once and load (DESIGN 10.3).
                Futures.logged(this.mutes.load(player.uuid(), player.address()), this.logger,
                        "load the mutes of " + player.name());
                yield cancellable
                        ? new ChatDecision.Denied(
                                this.messages.loading(this.messages.localeOf(player)))
                        : new ChatDecision.Allowed();
            }
            case MuteCache.Lookup.Muted muted -> cancellable
                    ? this.muted(player, muted.mute(), text, command)
                    : this.passed(player, command);
        };
    }

    private ChatDecision passed(final OnlinePlayer player, final boolean command) {
        final Instant now = this.clock.instant();
        final Instant last = this.lastPassedWarning.get();
        if (!now.isBefore(last.plus(PASSED_WARNING_INTERVAL))
                && this.lastPassedWarning.compareAndSet(last, now)) {
            this.logger.warn("{} is muted, but their {} went through: it is signed, and this"
                    + " proxy can't block signed chat without SignedVelocity (warned at most once"
                    + " a minute)", player.name(), command ? "command" : "chat message");
        }
        return new ChatDecision.Allowed();
    }

    private ChatDecision muted(final OnlinePlayer player, final Punishment mute,
            final String text, final boolean command) {
        final Denial denial = command ? Denial.COMMAND_MUTED
                : mute.shadow() ? Denial.CHAT_SHADOW_MUTED : Denial.CHAT_MUTED;
        final Component message = mute.shadow()
                ? Component.empty()
                : command
                        ? this.messages.commandMuted(mute, this.messages.localeOf(player))
                        : this.messages.chatMuted(mute, this.messages.localeOf(player));
        final SakuraPlayerDenyEvent event = this.events.post(new SakuraPlayerDenyEvent(
                player.uuid(), player.name(), player.address(), denial, mute, message));
        if (event.cancelled()) {
            return new ChatDecision.Allowed();
        }
        if (mute.shadow()) {
            return new ChatDecision.ShadowMuted();
        }
        if (!command) {
            this.notifications.attemptedChat(player, mute, text);
        }
        return new ChatDecision.Denied(event.message());
    }
}
