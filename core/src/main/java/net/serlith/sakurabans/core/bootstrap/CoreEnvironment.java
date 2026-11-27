package net.serlith.sakurabans.core.bootstrap;

import java.nio.file.Path;
import java.util.Map;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;

public record CoreEnvironment(Path dataDirectory, ComponentLogger logger, boolean proxy,
        Map<String, String> environment) {

    public CoreEnvironment {
        environment = Map.copyOf(environment);
    }

    /** Without an environment. */
    public CoreEnvironment(final Path dataDirectory, final ComponentLogger logger,
            final boolean proxy) {
        this(dataDirectory, logger, proxy, Map.of());
    }
}
