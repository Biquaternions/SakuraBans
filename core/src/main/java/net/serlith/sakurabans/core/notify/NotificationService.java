package net.serlith.sakurabans.core.notify;

import java.time.Duration;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.serlith.sakurabans.api.appeal.Appeal;
import net.serlith.sakurabans.api.event.EventPriority;
import net.serlith.sakurabans.api.event.PunishmentCreatedEvent;
import net.serlith.sakurabans.api.event.PunishmentRevokedEvent;
import net.serlith.sakurabans.api.event.Subscription;
import net.serlith.sakurabans.api.model.Punishment;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.model.Visibility;
import net.serlith.sakurabans.api.player.AltEntry;
import net.serlith.sakurabans.core.configuration.ConfigService;
import net.serlith.sakurabans.core.event.SakuraEventBus;
import net.serlith.sakurabans.core.event.SakuraPunishmentRevokedEvent;
import net.serlith.sakurabans.core.message.MessageKey;
import net.serlith.sakurabans.core.message.MessageService;
import net.serlith.sakurabans.core.message.Placeholders;
import net.serlith.sakurabans.core.permission.Permissions;
import net.serlith.sakurabans.core.platform.OnlinePlayer;
import net.serlith.sakurabans.core.platform.Platform;
import net.serlith.sakurabans.core.player.NameCache;
import org.jspecify.annotations.Nullable;

public final class NotificationService {

    private static final Duration ALT_NOTICE_REPEAT = Duration.ofMinutes(30);

    private final Platform platform;
    private final MessageService messages;
    private final Placeholders placeholders;
    private final NameCache names;
    private final ConfigService config;
    private final SakuraEventBus events;
    private final Throttle throttle;
    private final Executor worker;
    private final InstantSource clock;
    private final List<Subscription> subscriptions = new ArrayList<>();

    public NotificationService(final Platform platform, final MessageService messages,
            final Placeholders placeholders, final NameCache names, final ConfigService config,
            final SakuraEventBus events, final Throttle throttle, final Executor worker,
            final InstantSource clock) {
        this.platform = platform;
        this.messages = messages;
        this.placeholders = placeholders;
        this.names = names;
        this.config = config;
        this.events = events;
        this.throttle = throttle;
        this.worker = worker;
        this.clock = clock;
    }

    public void start() {
        this.subscriptions.add(this.events.subscribe(PunishmentCreatedEvent.class,
                EventPriority.MONITOR, event -> this.created(event.punishment())));
        this.subscriptions.add(this.events.subscribe(PunishmentRevokedEvent.class,
                EventPriority.MONITOR, event -> this.revoked(event.punishment(),
                        event instanceof SakuraPunishmentRevokedEvent own
                                ? own.visibility()
                                : Visibility.NORMAL)));
    }

    public void stop() {
        this.subscriptions.forEach(Subscription::unsubscribe);
        this.subscriptions.clear();
    }

    public void created(final Punishment punishment) {
        final PunishmentType type = punishment.type();
        final MessageKey key = switch (type) {
            case BAN -> MessageKey.NOTIFY_BAN;
            case MUTE -> MessageKey.NOTIFY_MUTE;
            case WARN -> MessageKey.NOTIFY_WARN;
            case KICK -> MessageKey.NOTIFY_KICK;
        };
        this.announce(punishment, key, punishment.visibility(), switch (punishment.visibility()) {
            case NORMAL -> List.of(Permissions.notify(type));
            case SILENT -> List.of(Permissions.notifySilent(type));
            case HIDDEN -> null;
        });
    }

    public void revoked(final Punishment punishment, final Visibility visibility) {
        final PunishmentType type = punishment.type();
        final MessageKey key = switch (type) {
            case BAN -> MessageKey.NOTIFY_UNBAN;
            case MUTE -> MessageKey.NOTIFY_UNMUTE;
            case WARN -> MessageKey.NOTIFY_UNWARN;
            case KICK -> throw new IllegalArgumentException("a kick can't be revoked");
        };
        this.announce(punishment, key, visibility, switch (visibility) {
            case NORMAL -> List.of(Permissions.notifyRevoke(type));
            case SILENT -> List.of(Permissions.notifyRevoke(type), Permissions.notifySilent(type));
            case HIDDEN -> null;
        });
    }

    public void attemptedJoin(final String name, final Punishment ban) {
        if (!this.throttle.tryAcquire("join:" + name.toLowerCase(Locale.ROOT),
                this.config.settings().notifications().attemptThrottle())) {
            return;
        }
        this.async(() -> this.send(Permissions.NOTIFY_ATTEMPT_JOIN, (locale, showAddresses) ->
                this.messages.render(MessageKey.NOTIFY_ATTEMPT_JOIN, locale,
                        this.punishmentTags(ban, locale, showAddresses),
                        Placeholder.unparsed("name", name))));
    }

    public void joinedWithAlts(final UUID uuid, final String name, final List<AltEntry> alts,
            final int limit) {
        if (!this.throttle.tryAcquire("alts:" + uuid, ALT_NOTICE_REPEAT)) {
            return;
        }
        this.async(() -> this.send(Permissions.ALTS_NOTIFY, (locale, showAddresses) -> {
            final List<Component> names = new ArrayList<>();
            for (final AltEntry alt : alts.subList(0, Math.min(limit, alts.size()))) {
                names.add(this.messages.render(alt.activePunishments().isEmpty()
                                ? MessageKey.NOTIFY_ALTS_NAME
                                : MessageKey.NOTIFY_ALTS_PUNISHED_NAME, locale,
                        Placeholder.unparsed("name", alt.account().name())));
            }
            return this.messages.render(MessageKey.NOTIFY_ALTS_JOIN, locale,
                    Placeholder.unparsed("name", name),
                    Placeholder.unparsed("count", Integer.toString(alts.size())),
                    Placeholder.component("alts", Component.join(JoinConfiguration.commas(true),
                            names)));
        }));
    }

    public void attemptedChat(final OnlinePlayer player, final Punishment mute,
            final String message) {
        if (!this.throttle.tryAcquire("chat:" + player.uuid(),
                this.config.settings().notifications().attemptThrottle())) {
            return;
        }
        this.async(() -> this.send(Permissions.NOTIFY_ATTEMPT_CHAT, (locale, showAddresses) ->
                this.messages.render(MessageKey.NOTIFY_ATTEMPT_CHAT, locale,
                        this.punishmentTags(mute, locale, showAddresses),
                        Placeholder.unparsed("name", player.name()),
                        Placeholder.unparsed("message", message))));
    }

    @FunctionalInterface
    private interface Renderer {
        Component render(Locale locale, boolean showAddresses);
    }

    private void announce(final Punishment punishment, final MessageKey key,
            final Visibility visibility, final @Nullable List<String> permissions) {
        final Renderer renderer = (locale, showAddresses) -> this.messages.render(key, locale,
                this.punishmentTags(punishment, locale, showAddresses),
                Placeholder.component("silent", visibility == Visibility.NORMAL
                        ? Component.empty()
                        : this.messages.render(MessageKey.NOTIFY_SILENT_TAG, locale)));
        if (permissions == null) {
            this.toConsole(renderer);
        } else {
            this.send(permissions, renderer);
        }
    }

    public void appealed(final Appeal appeal, final Punishment punishment, final String name) {
        final Renderer renderer = (locale, showAddresses) -> this.messages.render(
                MessageKey.NOTIFY_APPEAL, locale,
                Placeholder.unparsed("name", name),
                Placeholder.unparsed("id", Long.toString(appeal.id())),
                Placeholder.unparsed("display_id", punishment.displayId()),
                Placeholder.unparsed("message", appeal.message()));
        this.async(() -> this.send(Permissions.APPEALS_VIEW, renderer));
    }

    public void appealDecided(final Appeal appeal, final String displayId) {
        final Appeal.Decision decision = appeal.decision().orElse(null);
        if (decision == null) {
            return;
        }
        this.platform.player(appeal.appellant()).ifPresent(player -> player.sendMessage(
                this.messages.render(appeal.status() == Appeal.Status.ACCEPTED
                                ? MessageKey.APPEAL_DECIDED_ACCEPTED
                                : MessageKey.APPEAL_DECIDED_REJECTED,
                        player.locale().orElseGet(this.messages::defaultLocale),
                        Placeholder.unparsed("display_id", displayId),
                        Placeholder.unparsed("note", decision.note().orElse("")))));
    }

    public void broadcast(final String text) {
        final Renderer renderer = (locale, showAddresses) -> this.messages.render(
                MessageKey.BROADCAST_FORMAT, locale, Placeholder.component("message",
                        MiniMessage.miniMessage().deserialize(text)));
        this.async(() -> this.send(List.of(), renderer));
    }

    private void send(final String permission, final Renderer renderer) {
        this.send(List.of(permission), renderer);
    }

    private void send(final List<String> permissions, final Renderer renderer) {
        final boolean censor = this.config.settings().display().censorIpAddresses();
        final boolean players = this.config.settings().notifications().sendToPlayers();
        for (final OnlinePlayer player : players ? this.platform.onlinePlayers()
                : List.<OnlinePlayer>of()) {
            if (permissions.stream().allMatch(player::hasPermission)) {
                player.sendMessage(renderer.render(
                        player.locale().orElseGet(this.messages::defaultLocale),
                        !censor || player.hasPermission(Permissions.VIEW_IPS)));
            }
        }
        this.toConsole(renderer);
    }

    private void toConsole(final Renderer renderer) {
        final Audience console = this.platform.console();
        console.sendMessage(renderer.render(this.messages.defaultLocale(), true));
    }

    private TagResolver punishmentTags(final Punishment punishment, final Locale locale,
            final boolean showAddresses) {
        return this.placeholders.punishment(punishment, locale, this.clock.instant(),
                this.names::name, showAddresses);
    }

    private void async(final Runnable task) {
        try {
            this.worker.execute(task);
        } catch (final RejectedExecutionException ignored) {
            // Shutting down: the notice is lost, which is fine.
        }
    }

}
