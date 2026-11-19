package net.serlith.sakurabans.core.bootstrap;

import java.time.InstantSource;
import net.serlith.sakurabans.core.concurrent.Lifecycle;
import net.serlith.sakurabans.core.concurrent.SakuraExecutors;
import net.serlith.sakurabans.core.configuration.ConfigService;
import net.serlith.sakurabans.core.message.MessageService;
import net.serlith.sakurabans.core.platform.LatePlatform;

final class CoreComponents {

    // Layer 0
    private final CoreEnvironment environment;
    private final InstantSource clock;
    private final Lifecycle lifecycle;
    private final LatePlatform platform;
    private final SakuraExecutors executors;

    // Layer 1
    private final ConfigService config;
    private final MessageService messages;

    CoreComponents(final CoreEnvironment environment, final InstantSource clock) {
        this.environment = environment;
        this.clock = clock;
        this.lifecycle = new Lifecycle();
        this.platform = new LatePlatform();
        this.executors = new SakuraExecutors(
                failure -> environment.logger().error("A background task failed", failure));

        this.config = new ConfigService(environment.dataDirectory(), environment.logger());
        this.messages =
                new MessageService(environment.dataDirectory(), this.config, environment.logger());
    }

    CoreEnvironment environment() {
        return this.environment;
    }

    InstantSource clock() {
        return this.clock;
    }

    Lifecycle lifecycle() {
        return this.lifecycle;
    }

    LatePlatform platform() {
        return this.platform;
    }

    SakuraExecutors executors() {
        return this.executors;
    }

    ConfigService config() {
        return this.config;
    }

    MessageService messages() {
        return this.messages;
    }
}
