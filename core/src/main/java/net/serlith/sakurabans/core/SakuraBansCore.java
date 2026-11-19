package net.serlith.sakurabans.core;

import java.time.Duration;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import net.serlith.sakurabans.core.concurrent.Lifecycle;
import net.serlith.sakurabans.core.concurrent.LifecycleState;
import net.serlith.sakurabans.core.concurrent.SakuraExecutors;
import net.serlith.sakurabans.core.configuration.ConfigService;
import net.serlith.sakurabans.core.configuration.ReloadResult;
import net.serlith.sakurabans.core.message.MessageService;
import net.serlith.sakurabans.core.platform.LatePlatform;
import net.serlith.sakurabans.core.platform.Platform;
import net.serlith.sakurabans.core.util.BuildConstants;

public final class SakuraBansCore {

    private static final Duration SHUTDOWN_TIMEOUT = Duration.ofSeconds(10);

    private final ComponentLogger logger;
    private final Lifecycle lifecycle;
    private final LatePlatform platform;
    private final SakuraExecutors executors;
    private final ConfigService config;
    private final MessageService messages;

    public SakuraBansCore(final ComponentLogger logger, final Lifecycle lifecycle,
            final LatePlatform platform, final SakuraExecutors executors,
            final ConfigService config, final MessageService messages) {
        this.logger = logger;
        this.lifecycle = lifecycle;
        this.platform = platform;
        this.executors = executors;
        this.config = config;
        this.messages = messages;
    }

    /** Phase 2. On failure everything started so far is stopped again and the error rethrown. */
    public void start(final Platform platform) {
        this.lifecycle.advance(LifecycleState.CREATED, LifecycleState.STARTING);
        try {
            this.platform.bind(platform);
        } catch (final RuntimeException | Error e) {
            this.stop();
            throw e;
        }
        this.lifecycle.advance(LifecycleState.STARTING, LifecycleState.RUNNING);
        this.logger.info("SakuraBans {} ({}) enabled on {}", BuildConstants.VERSION,
                BuildConstants.GIT_COMMIT, platform.platformVersion());
    }

    /** Only while running; settings are read per use, so services see the new values at once. */
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

    /** Safe in every state and idempotent; waits up to 10 s for background tasks. */
    public void stop() {
        if (!this.lifecycle.advanceTo(LifecycleState.STOPPING)) {
            return;
        }
        if (!this.executors.shutdown(SHUTDOWN_TIMEOUT)) {
            this.logger.warn("Background tasks didn't finish within {} s and were interrupted",
                    SHUTDOWN_TIMEOUT.toSeconds());
        }
        this.lifecycle.advance(LifecycleState.STOPPING, LifecycleState.STOPPED);
        this.logger.info("SakuraBans {} disabled", BuildConstants.VERSION);
    }

    public LifecycleState state() {
        return this.lifecycle.state();
    }
}
