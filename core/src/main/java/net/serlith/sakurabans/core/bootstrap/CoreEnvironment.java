package net.serlith.sakurabans.core.bootstrap;

import java.nio.file.Path;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import net.serlith.sakurabans.core.platform.PlatformType;

public record CoreEnvironment(Path dataDirectory, ComponentLogger logger,
        PlatformType platformType) {
}
