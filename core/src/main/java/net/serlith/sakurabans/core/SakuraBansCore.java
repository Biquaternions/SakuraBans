package net.serlith.sakurabans.core;

import java.time.Duration;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import net.serlith.sakurabans.api.SakuraBans;
import net.serlith.sakurabans.api.SakuraBansApi;
import net.serlith.sakurabans.core.command.SakuraCommands;
import net.serlith.sakurabans.core.concurrent.Lifecycle;
import net.serlith.sakurabans.core.concurrent.LifecycleState;
import net.serlith.sakurabans.core.concurrent.SakuraExecutors;
import net.serlith.sakurabans.core.configuration.ConfigService;
import net.serlith.sakurabans.core.configuration.ReloadResult;
import net.serlith.sakurabans.core.enforce.ChannelReceiver;
import net.serlith.sakurabans.core.enforce.ChatGate;
import net.serlith.sakurabans.core.enforce.LoginGate;
import net.serlith.sakurabans.core.message.MessageService;
import net.serlith.sakurabans.core.messaging.ProxyChannel;
import net.serlith.sakurabans.core.notify.NotificationService;
import net.serlith.sakurabans.core.platform.LatePlatform;
import net.serlith.sakurabans.core.platform.Platform;
import net.serlith.sakurabans.core.punish.ExpirySweeper;
import net.serlith.sakurabans.core.punish.SakuraPunishmentService;
import net.serlith.sakurabans.core.scope.ServerNameDetector;
import net.serlith.sakurabans.core.storage.StorageService;
import net.serlith.sakurabans.core.util.BuildConstants;

public final class SakuraBansCore {

    private static final Duration SHUTDOWN_TIMEOUT = Duration.ofSeconds(10);

    private final ComponentLogger logger;
    private final Lifecycle lifecycle;
    private final LatePlatform platform;
    private final SakuraExecutors executors;
    private final ConfigService config;
    private final MessageService messages;
    private final StorageService storage;
    private final Services services;

    public record Services(NotificationService notifications, ExpirySweeper sweeper,
            SakuraPunishmentService punishments, LoginGate loginGate, ChatGate chatGate,
            SakuraCommands commands, ServerNameDetector serverNames, ProxyChannel channel,
            ChannelReceiver channelReceiver, SakuraApi api) {
    }

    public SakuraBansCore(final ComponentLogger logger, final Lifecycle lifecycle,
            final LatePlatform platform, final SakuraExecutors executors,
            final ConfigService config, final MessageService messages,
            final StorageService storage, final Services services) {
        this.logger = logger;
        this.lifecycle = lifecycle;
        this.platform = platform;
        this.executors = executors;
        this.config = config;
        this.messages = messages;
        this.storage = storage;
        this.services = services;
    }

    public void start(final Platform platform) {
        this.lifecycle.advance(LifecycleState.CREATED, LifecycleState.STARTING);
        try {
            this.platform.bind(platform);
            this.services.serverNames().load();
            this.storage.start();
            this.services.channel().load();
            this.services.notifications().start();
            this.services.sweeper().start();
            SakuraBans.register(this.services.api());
        } catch (final RuntimeException | Error e) {
            this.stop();
            throw e;
        }
        this.lifecycle.advance(LifecycleState.STARTING, LifecycleState.RUNNING);
        this.logger.info("SakuraBans {} ({}) enabled on {}", BuildConstants.VERSION,
                BuildConstants.GIT_COMMIT, platform.platformVersion());
    }

    public ReloadResult reload() {
        if (!this.lifecycle.running()) {
            throw new IllegalStateException("can't reload while " + this.lifecycle.state());
        }
        final ReloadResult result = this.config.reload();
        if (result instanceof ReloadResult.Reloaded) {
            this.messages.load();
        }
        return result;
    }

    public void stop() {
        if (!this.lifecycle.advanceTo(LifecycleState.STOPPING)) {
            return;
        }
        SakuraBans.unregister(this.services.api());
        this.services.sweeper().stop();
        this.services.notifications().stop();
        if (!this.executors.shutdown(SHUTDOWN_TIMEOUT)) {
            this.logger.warn("Background tasks didn't finish within {} s and were interrupted",
                    SHUTDOWN_TIMEOUT.toSeconds());
        }
        // After the worker: queued writes still had their connections.
        this.storage.stop();
        this.lifecycle.advance(LifecycleState.STOPPING, LifecycleState.STOPPED);
        this.logger.info("SakuraBans {} disabled", BuildConstants.VERSION);
    }

    public LifecycleState state() {
        return this.lifecycle.state();
    }

    public LoginGate loginGate() {
        return this.services.loginGate();
    }

    public ChatGate chatGate() {
        return this.services.chatGate();
    }

    public SakuraCommands commands() {
        return this.services.commands();
    }

    public SakuraPunishmentService punishments() {
        return this.services.punishments();
    }

    public ServerNameDetector serverNames() {
        return this.services.serverNames();
    }

    public ProxyChannel channel() {
        return this.services.channel();
    }

    public ChannelReceiver channelReceiver() {
        return this.services.channelReceiver();
    }

    public SakuraBansApi api() {
        return this.services.api();
    }
}
