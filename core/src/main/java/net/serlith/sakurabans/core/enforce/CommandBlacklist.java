package net.serlith.sakurabans.core.enforce;

import java.util.Locale;
import java.util.Set;
import net.serlith.sakurabans.core.configuration.ConfigService;

public final class CommandBlacklist {

    private final ConfigService config;

    public CommandBlacklist(final ConfigService config) {
        this.config = config;
    }

    public boolean blocked(final String commandLine) {
        String line = commandLine.strip();
        if (line.startsWith("/")) {
            line = line.substring(1);
        }
        final int space = line.indexOf(' ');
        final String label = (space < 0 ? line : line.substring(0, space)).toLowerCase(Locale.ROOT);
        if (label.isEmpty()) {
            return false;
        }
        final Set<String> blacklist = this.config.settings().enforcement().muteCommandBlacklist();
        if (blacklist.contains(label)) {
            return true;
        }
        final int colon = label.indexOf(':');
        return colon >= 0 && blacklist.contains(label.substring(colon + 1));
    }

}
