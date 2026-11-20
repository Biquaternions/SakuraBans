package net.serlith.sakurabans.core.bootstrap;

import java.time.InstantSource;
import net.serlith.sakurabans.core.SakuraBansCore;
import net.serlith.sakurabans.core.concurrent.LifecycleState;
import net.serlith.sakurabans.core.configuration.ConfigException;

public final class CoreBootstrap {

    private CoreBootstrap() {
    }

    /**
     * Throws {@link IllegalStateException} after logging every error if the configuration is
     * invalid, so the platform refuses to load the plugin.
     */
    public static SakuraBansCore create(final CoreEnvironment environment)
            throws IllegalStateException {
        return create(environment, InstantSource.system());
    }

    public static SakuraBansCore create(final CoreEnvironment environment,
            final InstantSource clock) throws IllegalStateException {
        return create(new CoreComponents(environment, clock));
    }

    static SakuraBansCore create(final CoreComponents components)
            throws IllegalStateException {
        try {
            components.config().load();
        } catch (final ConfigException e) {
            components.environment().logger().error(e.getMessage());
            throw new IllegalStateException(
                    "SakuraBans can't start: the configuration has errors (see above)", e);
        }
        components.messages().load();
        components.lifecycle().advance(LifecycleState.NEW, LifecycleState.CREATED);
        return new SakuraBansCore(components.environment().logger(), components.lifecycle(),
                components.platform(), components.executors(), components.config(),
                components.messages(), components.storage(),
                new SakuraBansCore.Services(components.notifications(), components.sweeper(),
                        components.punishmentService(),
                        components.loginGate(), components.chatGate()));
    }
}
