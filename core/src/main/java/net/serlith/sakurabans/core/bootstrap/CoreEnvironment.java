package net.serlith.sakurabans.core.bootstrap;

import java.nio.file.Path;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;

public record CoreEnvironment(Path dataDirectory, ComponentLogger logger, boolean proxy) {
}
