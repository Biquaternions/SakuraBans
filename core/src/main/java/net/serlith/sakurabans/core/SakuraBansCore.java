package net.serlith.sakurabans.core;

import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import net.serlith.sakurabans.core.util.BuildConstants;

/**
 * Lifecycle façade of the plugin core: the platforms call {@link #start()}, {@link #reload()} and
 * {@link #stop()}.
 *
 * <p>This is the S02 stub. S10 creates it through {@code CoreBootstrap} from the platform SPI and
 * gives it the startup and shutdown order of DESIGN 10.1.
 */
public final class SakuraBansCore {

    private final ComponentLogger logger;

    /**
     * Creates the core.
     *
     * @param logger the platform's logger for this plugin
     */
    public SakuraBansCore(final ComponentLogger logger) {
        this.logger = logger;
    }

    /**
     * Starts the core.
     */
    public void start() {
        this.logger.info("SakuraBans {} ({}) enabled", BuildConstants.VERSION,
                BuildConstants.GIT_COMMIT);
    }

    /**
     * Reloads the configuration.
     */
    public void reload() {
        // Configuration arrives in S08.
    }

    /**
     * Stops the core.
     */
    public void stop() {
        this.logger.info("SakuraBans {} disabled", BuildConstants.VERSION);
    }
}
